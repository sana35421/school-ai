package com.schoolai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolai.common.exception.ServiceException;
import com.schoolai.entity.Conversation;
import com.schoolai.entity.Message;
import com.schoolai.entity.MessageAttachment;
import com.schoolai.entity.UploadRecord;
import com.schoolai.entity.User;
import com.schoolai.mapper.ConversationMapper;
import com.schoolai.mapper.MessageAttachmentMapper;
import com.schoolai.mapper.MessageMapper;
import com.schoolai.mapper.UploadRecordMapper;
import com.schoolai.model.dto.ChatSendDTO;
import com.schoolai.model.vo.AttachmentVO;
import com.schoolai.model.vo.ConversationVO;
import com.schoolai.model.vo.MessageVO;
import com.schoolai.model.vo.SourceItemVO;
import com.schoolai.service.DifyClient;
import com.schoolai.service.IChatService;
import com.schoolai.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final SecurityUtils securityUtils;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final MessageAttachmentMapper messageAttachmentMapper;
    private final UploadRecordMapper uploadRecordMapper;
    private final DifyClient difyClient;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void streamSend(ChatSendDTO dto, HttpServletRequest request, HttpServletResponse response) {
        User user = securityUtils.getCurrentUser(request);
        if (user == null) {
            throw new ServiceException(401, "未登录");
        }

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer;
        try {
            writer = response.getWriter();
        } catch (IOException e) {
            log.error("无法获取响应输出流", e);
            return;
        }

        try {
            String difyConvId = dto.getConversationId();
            if (difyConvId != null && !difyConvId.isBlank()) {
                ensureConversationOwner(difyConvId, user.getId());
            }
            List<UploadRecord> attachments = resolveAttachments(dto.getAttachmentIds(), user.getId());
            List<Map<String, String>> difyFiles = attachments.stream()
                    .map(this::toDifyFile)
                    .toList();
            StringBuffer convIdBuf = new StringBuffer();
            StringBuilder fullAnswer = new StringBuilder();
            String[] sourcesJson = {null};

            writer.write("event: start\ndata: connected\n\n");
            writer.flush();

            difyClient.chatStream(normalizeCompetitionQuery(dto.getQuery()), user.getStudentId(), difyConvId, difyFiles, convIdBuf,
                    sourcePayload -> {
                        sourcesJson[0] = sourcePayload;
                        try {
                            writer.write("event: sources\n");
                            writer.write("data: {\"type\":\"sources\",\"sources\":" + sourcePayload + "}\n\n");
                            writer.flush();
                        } catch (Exception ex) {
                            log.warn("SSE sources write failed", ex);
                        }
                    }, chunk -> {
                fullAnswer.append(chunk);
                String safeChunk = chunk.replace("\n", "\\n").replace("\r", "");
                try {
                    writer.write("event: message\n");
                    writer.write("data: {\"type\":\"message\",\"answer\":\"" + escapeJson(safeChunk) + "\",\"conversation_id\":\"" + convIdBuf + "\"}\n\n");
                    writer.flush();
                } catch (Exception ex) {
                    log.warn("SSE write failed", ex);
                }
            });

            String finalConvId = convIdBuf.toString();

            if (finalConvId.isEmpty()) {
                throw new ServiceException(502, "智能助手未返回会话ID");
            }
            transactionTemplate.executeWithoutResult(status ->
                    saveChatResult(dto, user, attachments, finalConvId, fullAnswer.toString(), sourcesJson[0]));

            writer.write("event: done\n");
            writer.write("data: {\"type\":\"done\",\"conversation_id\":\"" + finalConvId + "\"}\n\n");
            writer.flush();
        } catch (Exception e) {
            log.error("Chat stream error", e);
            try {
                writer.write("event: error\n");
                writer.write("data: {\"type\":\"error\",\"message\":\"智能助手暂时无法处理该请求，请稍后重试\"}\n\n");
                writer.flush();
            } catch (Exception ignored) {
            }
        } finally {
            writer.close();
        }
    }

    private void saveChatResult(ChatSendDTO dto, User user, List<UploadRecord> attachments,
                                String conversationId, String answer, String sourcesJson) {
        Conversation existing = conversationMapper.findByConversationId(conversationId);
        if (existing == null) {
            Conversation conversation = new Conversation();
            conversation.setConversationId(conversationId);
            conversation.setUserId(user.getId());
            conversation.setTitle(dto.getQuery().length() > 20 ? dto.getQuery().substring(0, 20) + "..." : dto.getQuery());
            conversation.setCreatedAt(LocalDateTime.now());
            conversation.setLastActiveAt(LocalDateTime.now());
            conversationMapper.insert(conversation);
        } else {
            conversationMapper.updateLastActive(conversationId);
        }

        Message userMessage = new Message();
        userMessage.setUserId(user.getId());
        userMessage.setConversationId(conversationId);
        userMessage.setRole("user");
        userMessage.setContent(buildUserMessageContent(dto));
        userMessage.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(userMessage);
        saveMessageAttachments(userMessage.getId(), attachments);

        Message assistantMessage = new Message();
        assistantMessage.setUserId(user.getId());
        assistantMessage.setConversationId(conversationId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        assistantMessage.setSourcesJson(sourcesJson);
        assistantMessage.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(assistantMessage);
    }

    private String normalizeCompetitionQuery(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        String normalized = query;
        if (normalized.matches(".*(?i)(ACM|ICPC|CCPC|算法比赛|程序设计竞赛|算法竞赛).*")) {
            normalized += "。请按知识库中的“算法与程序设计竞赛”分类回答，优先查找该分类的指导老师信息；如果用户要求推荐队友，再返回该分类的具体队友。";
        }
        if (normalized.matches(".*(?i)(挑战杯).*")) {
            normalized += "。请按知识库中的“软件开发与创新项目竞赛”或挑战杯相关分类回答，优先查找指导老师信息。";
        }
        if (normalized.matches(".*(指导老师|指导教师|导师|教练|老师联系方式|老师邮箱).*")) {
            normalized += "。这是指导老师信息查询：只检索包含“专属指导老师、姓名、指导方向、指导内容、联系电话、工作邮箱”等字段的老师记录；不要使用队友记录回答，不要推荐队友。";
        }
        if (normalized.matches(".*(队友|组队|同学联系方式|同学邮箱).*")) {
            normalized += "。这是队友信息查询：只检索包含“竞赛方向、姓名、擅长方向、适合角色、推荐理由、联系方式、邮箱”的完整队友记录；不要使用指导老师记录回答。";
        }
        return normalized;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 10);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    @Override
    public List<ConversationVO> getHistory(int days, HttpServletRequest request) {
        User user = securityUtils.getCurrentUser(request);
        if (user == null) {
            return Collections.emptyList();
        }
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Conversation> conversations = conversationMapper.findRecentByUser(user.getId(), since);

        List<String> convIds = conversations.stream()
                .map(Conversation::getConversationId)
                .collect(Collectors.toList());
        List<Message> allMessages = convIds.isEmpty() ? Collections.emptyList()
                : messageMapper.findByConversationIds(convIds);

        Map<String, List<Message>> msgMap = allMessages.stream()
                .collect(Collectors.groupingBy(Message::getConversationId));

        return conversations.stream()
                .map(conv -> convertToConversationVO(conv, msgMap))
                .collect(Collectors.toList());
    }

    @Override
    public List<MessageVO> getMessages(String conversationId, HttpServletRequest request) {
        User user = securityUtils.getCurrentUser(request);
        if (user == null) {
            throw new ServiceException(401, "未登录");
        }
        Conversation conv = conversationMapper.findByConversationId(conversationId);
        if (conv == null || !conv.getUserId().equals(user.getId())) {
            throw new ServiceException(403, "无权访问");
        }
        List<Message> messages = messageMapper.findByConversationId(conversationId);
        return messages.stream().map(this::convertToMessageVO).collect(Collectors.toList());
    }

    @Override
    public void deleteConversation(String conversationId, HttpServletRequest request) {
        User user = securityUtils.getCurrentUser(request);
        if (user == null) {
            throw new ServiceException(401, "未登录");
        }
        Conversation conv = conversationMapper.findByConversationId(conversationId);
        if (conv == null || !conv.getUserId().equals(user.getId())) {
            throw new ServiceException(403, "无权删除");
        }
        List<Message> messages = messageMapper.findByConversationId(conv.getConversationId());
        for (Message msg : messages) {
            messageMapper.deleteById(msg.getId());
        }
        conversationMapper.deleteById(conv.getId());
    }

    private ConversationVO convertToConversationVO(Conversation conversation, Map<String, List<Message>> msgMap) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setConversationId(conversation.getConversationId());
        vo.setTitle(conversation.getTitle());
        vo.setCreatedAt(conversation.getCreatedAt());
        vo.setLastActiveAt(conversation.getLastActiveAt());
        List<Message> messages = msgMap.get(conversation.getConversationId());
        if (messages != null && !messages.isEmpty()) {
            messages.sort(Comparator.comparing(Message::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            vo.setSummary(messages.get(messages.size() - 1).getContent());
        }
        vo.setMessageCount(messages != null ? messages.size() : 0);
        return vo;
    }

    private MessageVO convertToMessageVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setSources(parseSources(message.getSourcesJson()));
        vo.setAttachments(loadMessageAttachments(message.getId()));
        return vo;
    }

    private List<SourceItemVO> parseSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(sourcesJson, new TypeReference<List<SourceItemVO>>() {});
        } catch (Exception e) {
            log.warn("Unable to deserialize message sources", e);
            return List.of();
        }
    }

    private String buildUserMessageContent(ChatSendDTO dto) {
        return dto.getQuery();
    }

    private void ensureConversationOwner(String conversationId, Long userId) {
        Conversation conversation = conversationMapper.findByConversationId(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new ServiceException(403, "无权访问该会话");
        }
    }

    List<UploadRecord> resolveAttachments(List<Long> attachmentIds, Long userId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        if (new HashSet<>(attachmentIds).size() != attachmentIds.size()) {
            throw new ServiceException(400, "不能重复添加同一个附件");
        }
        List<UploadRecord> records = uploadRecordMapper.selectBatchIds(attachmentIds);
        Map<Long, UploadRecord> recordMap = records.stream()
                .collect(Collectors.toMap(UploadRecord::getId, record -> record));
        List<UploadRecord> ordered = new ArrayList<>();
        for (Long attachmentId : attachmentIds) {
            UploadRecord record = recordMap.get(attachmentId);
            if (record == null || !userId.equals(record.getUserId())) {
                throw new ServiceException(403, "附件不存在或无权访问");
            }
            if (!"uploaded".equals(record.getStatus()) || record.getDifyFileId() == null || record.getDifyFileId().isBlank()) {
                throw new ServiceException(400, "附件尚未上传完成");
            }
            ordered.add(record);
        }
        return ordered;
    }

    private Map<String, String> toDifyFile(UploadRecord record) {
        Map<String, String> file = new HashMap<>();
        file.put("type", "document");
        file.put("transfer_method", "local_file");
        file.put("upload_file_id", record.getDifyFileId());
        return file;
    }

    private void saveMessageAttachments(Long messageId, List<UploadRecord> attachments) {
        for (UploadRecord attachment : attachments) {
            MessageAttachment relation = new MessageAttachment();
            relation.setMessageId(messageId);
            relation.setUploadRecordId(attachment.getId());
            relation.setCreatedAt(LocalDateTime.now());
            messageAttachmentMapper.insert(relation);
        }
    }

    private List<AttachmentVO> loadMessageAttachments(Long messageId) {
        List<MessageAttachment> relations = messageAttachmentMapper.selectList(
                new LambdaQueryWrapper<MessageAttachment>()
                        .eq(MessageAttachment::getMessageId, messageId)
                        .orderByAsc(MessageAttachment::getId));
        if (relations.isEmpty()) {
            return List.of();
        }
        List<Long> uploadIds = relations.stream()
                .map(MessageAttachment::getUploadRecordId)
                .toList();
        Map<Long, UploadRecord> uploads = uploadRecordMapper.selectBatchIds(uploadIds).stream()
                .collect(Collectors.toMap(UploadRecord::getId, record -> record));
        return uploadIds.stream()
                .map(uploads::get)
                .filter(Objects::nonNull)
                .map(this::toAttachmentVO)
                .toList();
    }

    private AttachmentVO toAttachmentVO(UploadRecord record) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setFileType(record.getFileType());
        vo.setFileSize(record.getFileSize());
        vo.setStatus(record.getStatus());
        return vo;
    }
}
