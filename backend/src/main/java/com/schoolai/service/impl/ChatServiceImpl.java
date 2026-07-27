package com.schoolai.service.impl;

import com.schoolai.common.exception.ServiceException;
import com.schoolai.entity.Conversation;
import com.schoolai.entity.Message;
import com.schoolai.entity.UploadRecord;
import com.schoolai.entity.User;
import com.schoolai.mapper.ConversationMapper;
import com.schoolai.mapper.MessageMapper;
import com.schoolai.mapper.UploadRecordMapper;
import com.schoolai.model.dto.ChatSendDTO;
import com.schoolai.model.vo.ConversationVO;
import com.schoolai.model.vo.MessageVO;
import com.schoolai.service.DifyClient;
import com.schoolai.service.IChatService;
import com.schoolai.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final SecurityUtils securityUtils;
    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final UploadRecordMapper uploadRecordMapper;
    private final DifyClient difyClient;

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
            String currentConvId = dto.getConversationId();
            StringBuffer convIdBuf = new StringBuffer(currentConvId != null ? currentConvId : "");

            StringBuffer fullAnswer = new StringBuffer();
            String difyConvId = (currentConvId != null && !currentConvId.isEmpty()) ? currentConvId : "";

            writer.write("event: start\ndata: connected\n\n");
            writer.flush();

            difyClient.chatStream(normalizeCompetitionQuery(dto.getQuery()), user.getStudentId(), difyConvId, convIdBuf, chunk -> {
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

            if (!finalConvId.isEmpty()) {
                Conversation existing = conversationMapper.findByConversationId(finalConvId);
                if (existing == null) {
                    Conversation conv = new Conversation();
                    conv.setConversationId(finalConvId);
                    conv.setUserId(user.getId());
                    conv.setTitle(dto.getQuery().length() > 20 ? dto.getQuery().substring(0, 20) + "..." : dto.getQuery());
                    conv.setCreatedAt(LocalDateTime.now());
                    conv.setLastActiveAt(LocalDateTime.now());
                    conversationMapper.insert(conv);
                } else {
                    conversationMapper.updateLastActive(finalConvId);
                }

                String userMsgContent = buildUserMessageContent(dto);
                Message userMsg = new Message();
                userMsg.setUserId(user.getId());
                userMsg.setConversationId(finalConvId);
                userMsg.setRole("user");
                userMsg.setContent(userMsgContent);
                userMsg.setCreatedAt(LocalDateTime.now());
                messageMapper.insert(userMsg);

                Message aiMsg = new Message();
                aiMsg.setUserId(user.getId());
                aiMsg.setConversationId(finalConvId);
                aiMsg.setRole("assistant");
                aiMsg.setContent(fullAnswer.toString());
                aiMsg.setCreatedAt(LocalDateTime.now());
                messageMapper.insert(aiMsg);
            }

            writer.write("event: done\n");
            writer.write("data: {\"type\":\"done\",\"conversation_id\":\"" + finalConvId + "\"}\n\n");
            writer.flush();
        } catch (Exception e) {
            log.error("Chat stream error", e);
            try {
                writer.write("event: error\n");
                writer.write("data: {\"type\":\"error\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}\n\n");
                writer.flush();
            } catch (Exception ignored) {
            }
        } finally {
            writer.close();
        }
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
        vo.setMessageCount(messages != null ? messages.size() : 0);
        return vo;
    }

    private MessageVO convertToMessageVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private String buildUserMessageContent(ChatSendDTO dto) {
        if (dto.getAttachmentIds() == null || dto.getAttachmentIds().isEmpty()) {
            return dto.getQuery();
        }
        List<UploadRecord> attachments = uploadRecordMapper.selectBatchIds(dto.getAttachmentIds());
        if (attachments.isEmpty()) {
            return dto.getQuery();
        }
        StringBuilder sb = new StringBuilder(dto.getQuery());
        sb.append("\n\n【附件信息】");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (UploadRecord att : attachments) {
            sb.append("\n- ").append(att.getFileName())
                    .append(" (").append(att.getFileType()).append(",")
                    .append(formatFileSize(att.getFileSize())).append(")")
                    .append(" 上传于 ").append(att.getCreatedAt() != null
                            ? att.getCreatedAt().format(fmt) : "");
        }
        return sb.toString();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
}