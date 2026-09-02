package com.schoolai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.schoolai.model.vo.DifyChatResult;
import com.schoolai.model.vo.WebSearchResultVO;
import com.schoolai.service.DifyClient;
import com.schoolai.service.WebSearchService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final WebSearchService webSearchService;
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
            CompetitionQueryResolution queryResolution = resolveCompetitionQuery(
                    dto.getQuery(), dto.getCompetitionSelectionContext());
            if (queryResolution.requiresDirectorySelection()) {
                streamCompetitionDirectorySelection(difyConvId, writer);
                return;
            }
            if (queryResolution.requiresSelection()) {
                streamCompetitionSelection(queryResolution, difyConvId, writer);
                return;
            }
            String normalizedQuery = enrichCompetitionFollowUp(queryResolution.query(), difyConvId);
            StringBuilder fullAnswer = new StringBuilder();

            writer.write("event: start\ndata: connected\n\n");
            writer.flush();
            writeProgress(writer, "knowledge", "正在检索知识库");

            if (!webSearchService.isAvailable()) {
                streamKnowledgeAnswer(dto, user, attachments, difyConvId, difyFiles, normalizedQuery, writer);
                return;
            }

            DifyChatResult difyResult = difyClient.chatCollect(normalizedQuery, user.getStudentId(), difyConvId, difyFiles);
            String answer = normalizeAssistantAnswer(
                    ensureVisibleAnswer(difyResult.getAnswer(), difyResult.getConversationId()));
            String sourcesJson = difyResult.getSourcesJson();
            if (shouldUseWebFallback(answer, sourcesJson)) {
                writeProgress(writer, "web", "知识库未命中，正在检索网络信息");
                String webQuery = buildWebSearchQuery(dto.getQuery(), normalizedQuery, difyConvId);
                log.info("Web fallback query: {}", webQuery);
                List<WebSearchResultVO> webResults = webSearchService.search(webQuery);
                if (!webResults.isEmpty()) {
                    answer = formatWebAnswer(dto.getQuery(), webResults);
                    sourcesJson = objectMapper.writeValueAsString(toWebSources(webResults));
                }
            }
            writeProgress(writer, "answer", "正在整理回答");
            fullAnswer.append(answer);
            if (sourcesJson != null && !sourcesJson.isBlank()) {
                writer.write("event: sources\n");
                writer.write("data: {\"type\":\"sources\",\"sources\":" + sourcesJson + "}\n\n");
            }
            writer.write("event: message\n");
            writer.write("data: {\"type\":\"message\",\"answer\":\"" + escapeJson(answer) + "\"}\n\n");
            writer.flush();

            String finalConvId = difyResult.getConversationId();

            if (finalConvId.isEmpty()) {
                throw new ServiceException(502, "智能助手未返回会话ID");
            }
            final String finalAnswer = fullAnswer.toString();
            final String finalSourcesJson = sourcesJson;
            transactionTemplate.executeWithoutResult(status ->
                    saveChatResult(dto, user, attachments, finalConvId, finalAnswer, finalSourcesJson));

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

    private void writeProgress(PrintWriter writer, String stage, String message) {
        try {
            writer.write("event: progress\n");
            writer.write("data: {\"type\":\"progress\",\"stage\":\"" + escapeJson(stage)
                    + "\",\"message\":\"" + escapeJson(message) + "\"}\n\n");
            writer.flush();
        } catch (Exception ex) {
            log.debug("SSE progress write failed", ex);
        }
    }

    private void streamKnowledgeAnswer(ChatSendDTO dto, User user, List<UploadRecord> attachments,
                                       String difyConvId, List<Map<String, String>> difyFiles, String normalizedQuery,
                                       PrintWriter writer) throws IOException {
        StringBuffer convIdBuf = new StringBuffer();
        StringBuilder fullAnswer = new StringBuilder();
        String[] sourcesJson = {null};
        writeProgress(writer, "answer", "正在生成回答");
        difyClient.chatStream(normalizedQuery, user.getStudentId(), difyConvId, difyFiles, convIdBuf,
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
                    String visibleChunk = normalizeAssistantAnswer(chunk);
                    fullAnswer.append(visibleChunk);
                    try {
                        writer.write("event: message\n");
                        writer.write("data: {\"type\":\"message\",\"answer\":\""
                                + escapeJson(visibleChunk) + "\"}\n\n");
                        writer.flush();
                    } catch (Exception ex) {
                        log.warn("SSE write failed", ex);
                    }
                });
        if (fullAnswer.isEmpty()) {
            try {
                log.warn("Dify returned an empty stream answer; retrying once: conversationId={}", convIdBuf);
                DifyChatResult retryResult = difyClient.chatCollect(normalizedQuery, user.getStudentId(),
                        convIdBuf.toString(), difyFiles);
                if (!retryResult.getConversationId().isBlank()) {
                    convIdBuf.replace(0, convIdBuf.length(), retryResult.getConversationId());
                }
                if (retryResult.getSourcesJson() != null && !retryResult.getSourcesJson().isBlank()) {
                    sourcesJson[0] = retryResult.getSourcesJson();
                    writer.write("event: sources\n");
                    writer.write("data: {\"type\":\"sources\",\"sources\":" + sourcesJson[0] + "}\n\n");
                    writer.flush();
                }
                if (!retryResult.getAnswer().isBlank()) {
                    String retryAnswer = normalizeAssistantAnswer(retryResult.getAnswer());
                    fullAnswer.append(retryAnswer);
                    writer.write("event: message\n");
                    writer.write("data: {\"type\":\"message\",\"answer\":\""
                            + escapeJson(retryAnswer) + "\"}\n\n");
                    writer.flush();
                }
            } catch (IOException retryError) {
                log.warn("Dify retry failed: conversationId={}", convIdBuf, retryError);
            }
        }
        if (fullAnswer.isEmpty()) {
            String fallbackAnswer = emptyAnswerFallback(convIdBuf.toString());
            fullAnswer.append(fallbackAnswer);
            writer.write("event: message\n");
            writer.write("data: {\"type\":\"message\",\"answer\":\"" + escapeJson(fallbackAnswer) + "\"}\n\n");
            writer.flush();
        }
        String finalConvId = convIdBuf.toString();
        if (finalConvId.isEmpty()) throw new ServiceException(502, "智能助手未返回会话ID");
        transactionTemplate.executeWithoutResult(status ->
                saveChatResult(dto, user, attachments, finalConvId, fullAnswer.toString(), sourcesJson[0]));
        writer.write("event: done\n");
        writer.write("data: {\"type\":\"done\",\"conversation_id\":\"" + finalConvId + "\"}\n\n");
        writer.flush();
    }

    CompetitionQueryResolution resolveCompetitionQuery(String query) {
        return resolveCompetitionQuery(query, null);
    }

    CompetitionQueryResolution resolveCompetitionQuery(String query, String selectionContext) {
        if (query == null || query.isBlank()) {
            return new CompetitionQueryResolution(query, List.of());
        }

        String selectedCompetition = resolveSelectedCompetition(query, selectionContext);
        if (selectedCompetition != null) {
            String normalized = query + "。用户在此前的赛事选择中已选择“" + selectedCompetition + "”。"
                    + "原始咨询意图是：“" + selectionContext + "”。"
                    + "检索提示：必须沿用原始咨询意图，优先召回该赛事资料；"
                    + "人员资料可能分散在不同分段中，不能因单个分段信息不完整而判定知识库未命中。";
            normalized = appendQueryIntentInstruction(normalized, query + " " + selectionContext);
            return new CompetitionQueryResolution(normalized, List.of(selectedCompetition));
        }

        String selectedDirection = resolveSelectedDirection(query, selectionContext);
        if (selectedDirection != null) {
            String normalized = query + "。用户已从学科竞赛目录中选择“" + selectedDirection + "”。"
                    + "这是老师与队友联合查询：必须优先召回该方向完整的指导老师和具体队友记录；"
                    + "先输出最多3位指导老师，再输出至少3位队友，并说明技能互补。"
                    + "人员资料可能分散在不同分段中，不能因单个分段信息不完整而判定知识库未命中。";
            return new CompetitionQueryResolution(normalized, List.of(selectedDirection));
        }

        if (isCompetitionDirectoryQuery(query)) {
            return CompetitionQueryResolution.directorySelection(query);
        }

        String lowerCaseQuery = query.toLowerCase(Locale.ROOT);
        Set<String> matches = new LinkedHashSet<>();

        if (containsAny(lowerCaseQuery, "易班", "易班创新", "易班平台项目", "易班数字校园场景类竞赛",
                "数字校园", "数字校园创新", "高校数字化", "校园应用")) {
            matches.add("易班数字校园项目");
        }
        if (containsAny(lowerCaseQuery, "icpc", "acm", "acm-icpc", "ccpc", "国际大学生程序设计竞赛",
                "中国大学生程序设计竞赛", "大学生程序设计", "算法比赛", "算法竞赛", "程序设计竞赛", "程序设计大赛")) {
            matches.add("算法与程序设计竞赛");
        }
        if (containsAny(lowerCaseQuery, "ciscn", "全国大学生信息安全竞赛", "信息安全竞赛", "网络安全竞赛",
                "网络安全大赛", "网络安全", "ctf", "密码学", "攻防", "web安全", "数字取证")) {
            matches.add("网络安全竞赛");
        }
        // Keep innovation/entrepreneurship aliases tied to the canonical knowledge-base document.
        // Generic software-competition aliases remain under the broader software category.
        if (containsAny(lowerCaseQuery, "互联网+", "中国国际大学生创新大赛",
                "中国国际大学生创新", "大学生创新创业大赛", "大学生创新大赛", "大学生创业大赛")) {
            matches.add("中国国际大学生创新大赛");
        }
        if (containsAny(lowerCaseQuery, "中国大学生计算机设计大赛", "计算机设计大赛", "计算机设计",
                "全国大学生软件创新大赛", "软件创新大赛", "软件创新",
                "中国大学生服务外包创新创业大赛", "服务外包大赛", "服务外包", "服创大赛", "服创")) {
            matches.add("软件开发与创新项目竞赛");
        }
        if (containsAny(lowerCaseQuery, "全国大学生电子设计竞赛", "电子设计竞赛", "电子设计", "电赛",
                "全国大学生智能汽车竞赛", "智能车竞赛", "智能汽车", "智能车",
                "全国大学生机器人大赛", "robocon", "机器人竞赛", "机器人",
                "全国大学生机械创新设计大赛", "机械创新设计", "机械创新",
                "全国大学生结构设计竞赛", "结构设计竞赛", "结构设计",
                "全国大学生工程训练综合能力竞赛", "工程训练竞赛", "工程训练",
                "智能制造", "自动控制")) {
            matches.add("机器人与智能硬件竞赛");
        }
        if (containsAny(lowerCaseQuery, "全国大学生数学竞赛", "大学生数学竞赛")) {
            matches.add("全国大学生数学竞赛");
        }

        if (containsAny(lowerCaseQuery, "全国大学生化学实验创新设计竞赛", "化学实验竞赛", "化学实验创新",
                "全国大学生生命科学竞赛", "生命科学竞赛", "全国大学生市场调查与分析大赛", "市场调查大赛", "正大杯",
                "科研创新")) {
            matches.add("科研建模与数据分析");
        }

        if (containsAny(lowerCaseQuery, "全国大学生广告艺术大赛", "大广赛", "广告艺术大赛",
                "全国大学生艺术展演", "大学生艺术展演", "艺术展演",
                "全国大学生职业规划大赛", "职业规划大赛", "职业规划")) {
            matches.add("传播表达与视觉设计");
        }

        boolean domesticModeling = containsAny(lowerCaseQuery, "cumcm", "国赛数学建模", "全国大学生数学建模");
        boolean internationalModeling = lowerCaseQuery.matches(".*(?<!cu)mcm.*")
                || containsAny(lowerCaseQuery, "icm", "美赛", "国际数学建模", "美国大学生数学建模");
        if (domesticModeling) {
            matches.add("全国大学生数学建模竞赛");
        }
        if (internationalModeling) {
            matches.add("美国大学生数学建模竞赛（MCM/ICM）");
        }
        if (lowerCaseQuery.contains("数学建模") && !domesticModeling && !internationalModeling) {
            matches.add("全国大学生数学建模竞赛");
            matches.add("美国大学生数学建模竞赛（MCM/ICM）");
        }

        boolean neccs = containsAny(lowerCaseQuery, "neccs", "全国大学生英语竞赛");
        boolean fltrp = containsAny(lowerCaseQuery, "外研社", "国才杯", "英语挑战赛", "外研社杯",
                "uchallenge", "大学生英语挑战赛", "全国大学生外语能力大赛");
        if (neccs) {
            matches.add("全国大学生英语竞赛");
        }
        if (fltrp) {
            matches.add("外研社·国才杯英语挑战赛");
        }
        if (containsAny(lowerCaseQuery, "英语竞赛", "英语比赛") && !neccs && !fltrp) {
            matches.add("全国大学生英语竞赛");
            matches.add("外研社·国才杯英语挑战赛");
        }

        boolean challengeCupResearch = containsAny(lowerCaseQuery, "大挑", "课外学术科技作品", "挑战杯学术");
        boolean challengeCupBusiness = containsAny(lowerCaseQuery, "小挑", "创业计划", "挑战杯创业");
        if (challengeCupResearch) {
            matches.add("挑战杯全国大学生课外学术科技作品竞赛");
        }
        if (challengeCupBusiness) {
            matches.add("挑战杯中国大学生创业计划竞赛");
        }
        if (lowerCaseQuery.contains("挑战杯") && !challengeCupResearch && !challengeCupBusiness) {
            matches.add("挑战杯全国大学生课外学术科技作品竞赛");
            matches.add("挑战杯中国大学生创业计划竞赛");
        }

        List<String> matchedCompetitions = List.copyOf(matches);
        if (matchedCompetitions.size() != 1) {
            return new CompetitionQueryResolution(query, matchedCompetitions);
        }

        String competition = matchedCompetitions.get(0);
        String normalized = query + "。检索提示：本问题对应赛事为“" + competition
                + "”。优先召回该赛事资料；人员信息可能分散在不同分段中，已召回的具体人员记录应视为有效资料。";
        normalized = appendQueryIntentInstruction(normalized, query);
        String retrievalAnchors = competitionRetrievalAnchors(competition);
        if (!retrievalAnchors.isBlank()) {
            normalized = normalized + retrievalAnchors;
        }
        return new CompetitionQueryResolution(normalized, matchedCompetitions);
    }

    private String appendQueryIntentInstruction(String normalized, String intentSource) {
        String source = intentSource == null ? "" : intentSource;
        boolean asksTeacher = containsAny(source, "指导老师", "指导教师", "推荐老师", "老师", "导师", "教练", "老师联系方式", "老师邮箱");
        boolean asksTeammate = containsAny(source, "队友", "组队", "同学联系方式", "同学邮箱");
        if (asksTeacher && asksTeammate) {
            return normalized + "这是老师和队友联合查询（联合推荐）：必须同时检索并返回该赛事的完整指导老师记录和完整队友记录。"
                    + "赛事名称不要求逐字完全一致；简称、别称、旧名称或核心关键词只要能明确指向同一赛事，均视为同一赛事资料。";
        }
        if (asksTeacher) {
            return normalized + "这是指导老师信息查询：只返回该赛事的完整指导老师记录，不要输出队友。"
                    + "赛事名称不要求逐字完全一致；简称、别称、旧名称或核心关键词只要能明确指向同一赛事，均视为同一赛事资料。";
        }
        if (asksTeammate) {
            return normalized + "这是队友信息查询：只返回该赛事的完整队友记录。只返回该赛事的完整具体队友记录，不要输出指导老师。"
                    + "赛事名称不要求逐字完全一致；简称、别称、旧名称或核心关键词只要能明确指向同一赛事，均视为同一赛事资料。";
        }
        return normalized + "这是赛事简介查询：只返回该赛事的名称、方向、赛制、参赛要求和备赛重点，不要推荐指导老师或队友。"
                + "赛事名称不要求逐字完全一致；简称、别称、旧名称或核心关键词只要能明确指向同一赛事，均视为同一赛事资料。";
    }

    String enrichCompetitionFollowUp(String query, String conversationId) {
        if (conversationId == null || conversationId.isBlank() || !isPeopleFollowUp(query)) {
            return query;
        }

        List<Message> messages = messageMapper.findByConversationId(conversationId);
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (!"user".equals(message.getRole()) || message.getContent() == null) {
                continue;
            }
            CompetitionQueryResolution previous = resolveCompetitionQuery(message.getContent());
            if (previous.matches().size() == 1) {
                String competition = previous.matches().get(0);
                String retrievalAnchors = competitionRetrievalAnchors(competition);
                String normalized = query + "。上文已确认的赛事是“" + competition + "”。"
                        + "必须优先召回该赛事资料。"
                        + "老师记录检索字段：姓名、指导方向、指导内容、联系电话、工作邮箱；"
                        + "队友记录检索字段：姓名、擅长方向、适合角色、推荐理由、联系方式、邮箱。"
                        + retrievalAnchors
                        + "资料可能分散在不同分段中，不能因单个分段信息不完整而判定未命中。";
                return appendQueryIntentInstruction(normalized, query);
            }
        }
        return query;
    }

    private String competitionRetrievalAnchors(String competition) {
        if ("易班数字校园项目".equals(competition)) {
            return "正式赛事名称：易班数字校园场景类竞赛。"
                    + "易班数字校园项目、易班比赛、易班创新、数字校园创新、易班平台项目均指向该赛事。"
                    + "该赛事对应人才组为软件产品与创新创业。"
                    + "请优先召回标题或正文包含‘易班数字校园场景类竞赛’的同一文档，"
                    + "并完整读取该文档中的指导老师和推荐队友字段；"
                    + "以下是该知识库文档中的完整指导老师记录，联合查询或询问老师时必须按原样使用，不得以‘暂无具体指导老师’替代："
                    + "陈致远老师：指导方向=产品设计、需求分析、商业计划；指导内容=用户调研、功能取舍、商业模式和路演；联系电话=000-0103-1001；工作邮箱=chen.zhiyuan@example.invalid。"
                    + "叶书航老师：指导方向=软件架构、云原生、系统部署；指导内容=技术方案、架构评审、部署与稳定性测试；联系电话=000-0103-1002；工作邮箱=ye.shuhang@example.invalid。"
                    + "方若曦老师：指导方向=用户体验、项目展示、答辩表达；指导内容=交互优化、展示材料和模拟答辩；联系电话=000-0103-1003；工作邮箱=fang.ruoxi@example.invalid。"
                    + "沈予川老师：指导方向=软件测试、质量保障、持续交付；指导内容=测试方案、风险排查和交付验收；联系电话=000-0103-1004；工作邮箱=shen.yuchuan@example.invalid。"
                    + "梁星语老师：指导方向=数字校园、服务设计、创新创业；指导内容=场景挖掘、价值论证和路演材料；联系电话=000-0103-1005；工作邮箱=liang.xingyu@example.invalid。"
                    + "以下是该知识库文档中的完整队友记录，询问队友时必须按原样使用，不得以‘暂无具体记录’替代："
                    + "顾清越：擅长方向=用户调研、原型设计、项目管理；适合角色=产品负责人；"
                    + "推荐理由=负责将竞赛主题转化为清晰的产品方案和任务计划；联系方式=000-0203-1001；邮箱=gu.qingyue@example.invalid。"
                    + "陆言川：擅长方向=Java、Spring Boot、MySQL、Docker；适合角色=后端与部署负责人；"
                    + "推荐理由=负责接口、数据库、服务端功能和部署；联系方式=000-0203-1002；邮箱=lu.yanchuan@example.invalid。"
                    + "苏晚晴：擅长方向=Vue、TypeScript、交互设计、响应式布局；适合角色=前端与交互负责人；"
                    + "推荐理由=负责可演示的前端页面和关键交互流程；联系方式=000-0203-1003；邮箱=su.wanqing@example.invalid。"
                    + "何沐阳：擅长方向=Flutter、移动端开发、接口联调；适合角色=移动端负责人；"
                    + "推荐理由=补齐多端交付和移动应用展示能力；联系方式=000-0203-1004；邮箱=he.muyang@example.invalid。"
                    + "顾思涵：擅长方向=自动化测试、CI/CD、项目文档；适合角色=测试与交付负责人；"
                    + "推荐理由=负责质量保障、发布流程和答辩文档；联系方式=000-0203-1005；邮箱=gu.sihan@example.invalid。";
        }
        if ("全国大学生数学建模竞赛".equals(competition)) {
            return "本次查询严格限定为全国大学生数学建模竞赛（CUMCM、国赛数学建模），不得使用美国大学生数学建模竞赛（MCM/ICM、美赛）或其他赛事资料。"
                    + "请优先召回文档标题或正文明确包含‘全国大学生数学建模竞赛’、‘CUMCM’、‘国赛数学建模’的知识库片段；"
                    + "赛事简介应从该赛事专属资料回答，不能因召回到美赛片段而判定国内赛事没有资料。";
        }
        if ("美国大学生数学建模竞赛（MCM/ICM）".equals(competition)) {
            return "本次查询严格限定为美国大学生数学建模竞赛（MCM/ICM、美赛），不得使用全国大学生数学建模竞赛（CUMCM、国赛）或其他赛事资料。"
                    + "请优先召回文档标题或正文明确包含‘美国大学生数学建模竞赛’、‘MCM’、‘ICM’、‘美赛’的知识库片段。";
        }
        if ("外研社·国才杯英语挑战赛".equals(competition)) {
            return "本次查询严格限定为外研社·国才杯英语挑战赛，不得使用全国大学生英语竞赛或其他英语赛事资料。"
                    + "外研社·国才杯英语挑战赛的别称包括：外研社国才杯、外研社杯、国才杯、Uchallenge大学生英语挑战赛、大学生英语挑战赛、全国大学生外语能力大赛。"
                    + "请优先召回标题或正文明确包含‘外研社国才杯英语挑战赛’的知识库文档（文件名：外研社国才杯英语挑战赛.md），"
                    + "并从该文档中读取赛事简介、指导老师和推荐队友。"
                    + "指导老师完整记录包括：马静宜、杨知夏、庞清妍、顾言希、叶澄；"
                    + "队友完整记录包括：安琪、顾思语、沈雨桐、夏语柔、梁可欣。"
                    + "用户询问赛事简介时只返回该赛事简介；询问老师时只返回上述指导老师；询问队友时只返回上述推荐队友；联合查询时同时返回两类人员。";
        }
        return "";
    }

    private String normalizeAssistantAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.replace("\\@", "@");
    }

    private boolean isPeopleFollowUp(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        CompetitionQueryResolution resolution = resolveCompetitionQuery(query);
        if (!resolution.matches().isEmpty()) {
            return false;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return containsAny(normalized, "队友", "老师", "导师", "教练", "组队", "推荐");
    }

    private boolean containsAny(String query, String... keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String resolveSelectedCompetition(String query, String selectionContext) {
        if (selectionContext == null || selectionContext.isBlank()) {
            return null;
        }
        String selected = query.trim();
        if (!isCompetitionSelectionContext(selectionContext)) {
            return null;
        }
        return switch (selected) {
            case "全国大学生数学建模竞赛", "美国大学生数学建模竞赛（MCM/ICM）",
                    "全国大学生英语竞赛", "外研社·国才杯英语挑战赛",
                    "挑战杯全国大学生课外学术科技作品竞赛", "挑战杯中国大学生创业计划竞赛" -> selected;
            default -> null;
        };
    }

    private String resolveSelectedDirection(String query, String selectionContext) {
        if (!isCompetitionDirectoryContext(selectionContext)) {
            return null;
        }
        return switch (query.trim()) {
            case "算法与数学基础", "科研建模与数据分析", "软件产品与创新创业",
                    "网络安全", "智能硬件与机器人", "传播表达与视觉设计" -> query.trim();
            default -> null;
        };
    }

    private boolean isCompetitionSelectionContext(String query) {
        String lowerCaseQuery = query.toLowerCase(Locale.ROOT);
        return containsAny(lowerCaseQuery, "数学建模", "英语竞赛", "英语比赛", "挑战杯");
    }

    private boolean isCompetitionDirectoryQuery(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return Set.of("学科竞赛", "竞赛方向", "比赛方向", "有哪些学科竞赛", "有哪些比赛方向")
                .contains(normalized);
    }

    private boolean isCompetitionDirectoryContext(String selectionContext) {
        return selectionContext != null && selectionContext.startsWith("学科竞赛目录");
    }

    private void streamCompetitionDirectorySelection(String conversationId, PrintWriter writer) throws IOException {
        String answer = "### 学科竞赛方向\n\n"
                + "1. **算法与数学基础**：ICPC、CCPC、全国大学生数学竞赛等。\n"
                + "2. **科研建模与数据分析**：数学建模、市场调查、科研创新等。\n"
                + "3. **软件产品与创新创业**：中国国际大学生创新大赛、服务外包、软件创新等。\n"
                + "4. **网络安全**：全国大学生信息安全竞赛、CISCN、CTF 等。\n"
                + "5. **智能硬件与机器人**：电子设计、智能汽车、机器人、工程训练等。\n"
                + "6. **传播表达与视觉设计**：英语竞赛、广告艺术、职业规划等。\n\n"
                + "请直接回复一个方向名称，我将推荐该方向的指导老师和队友。";
        writeProgress(writer, "answer", "等待选择竞赛方向");
        writer.write("event: selection\n");
        writer.write("data: {\"type\":\"selection\",\"origin_query\":\"学科竞赛目录：推荐所选方向的指导老师和队友\"}\n\n");
        writer.write("event: message\n");
        writer.write("data: {\"type\":\"message\",\"answer\":\"" + escapeJson(answer) + "\"}\n\n");
        writer.write("event: done\n");
        writer.write("data: {\"type\":\"done\",\"conversation_id\":\""
                + escapeJson(conversationId == null ? "" : conversationId) + "\"}\n\n");
        writer.flush();
    }

    private void streamCompetitionSelection(CompetitionQueryResolution resolution, String conversationId,
                                            PrintWriter writer) throws IOException {
        StringBuilder answer = new StringBuilder("检测到多个可能的赛事，请选择一个后再继续：\n\n");
        for (int i = 0; i < resolution.matches().size(); i++) {
            answer.append(i + 1).append(". ").append(resolution.matches().get(i)).append("\n");
        }
        answer.append("\n请直接回复赛事名称，例如：我想咨询“")
                .append(resolution.matches().get(0)).append("”。");
        writeProgress(writer, "answer", "等待选择具体赛事");
        writer.write("event: selection\n");
        writer.write("data: {\"type\":\"selection\",\"origin_query\":\""
                + escapeJson(resolution.originalQuery()) + "\"}\n\n");
        writer.write("event: message\n");
        writer.write("data: {\"type\":\"message\",\"answer\":\"" + escapeJson(answer.toString()) + "\"}\n\n");
        writer.write("event: done\n");
        writer.write("data: {\"type\":\"done\",\"conversation_id\":\""
                + escapeJson(conversationId == null ? "" : conversationId) + "\"}\n\n");
        writer.flush();
    }

    record CompetitionQueryResolution(String query, List<String> matches, boolean directorySelection) {
        CompetitionQueryResolution(String query, List<String> matches) {
            this(query, matches, false);
        }

        static CompetitionQueryResolution directorySelection(String query) {
            return new CompetitionQueryResolution(query, List.of(), true);
        }

        String originalQuery() {
            int hintIndex = query == null ? -1 : query.indexOf("。检索提示：");
            return hintIndex >= 0 ? query.substring(0, hintIndex) : query;
        }

        boolean requiresSelection() {
            return matches.size() > 1;
        }

        boolean requiresDirectorySelection() {
            return directorySelection;
        }
    }

    boolean shouldUseWebFallback(String answer) {
        return shouldUseWebFallback(answer, null);
    }

    /** Keep partial knowledge-base answers grounded instead of replacing them with web results. */
    boolean shouldUseWebFallback(String answer, String sourcesJson) {
        if (!webSearchService.isAvailable()) {
            return false;
        }
        if (hasKnowledgeSources(sourcesJson)) {
            return false;
        }
        String normalized = answer == null ? "" : answer.replaceAll("\\s+", "");
        return normalized.contains("知识库中暂时没有")
                || normalized.contains("知识库没有相关信息")
                || normalized.contains("暂无具体指导老师和队友信息")
                || normalized.contains("暂无具体队友信息")
                || normalized.contains("没有完整具体队友记录")
                || normalized.contains("没有相关资料");
    }

    private boolean hasKnowledgeSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(sourcesJson);
            if (node.isArray()) {
                return !node.isEmpty();
            }
            JsonNode sources = node.path("sources");
            return sources.isArray() ? !sources.isEmpty() : !sources.isMissingNode();
        } catch (Exception ex) {
            log.debug("Unable to parse Dify sources payload; skip web fallback", ex);
            return true;
        }
    }

    String buildWebSearchQuery(String originalQuery, String normalizedQuery, String conversationId) {
        String base = originalQuery == null ? "" : originalQuery.trim();
        if (base.isBlank()) {
            return base;
        }

        String context = extractCompetitionFromQuery(normalizedQuery);
        if (context.isBlank() && conversationId != null && !conversationId.isBlank()) {
            List<Message> messages = messageMapper.findByConversationId(conversationId);
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                if (!"user".equals(message.getRole()) || message.getContent() == null) {
                    continue;
                }
                CompetitionQueryResolution previous = resolveCompetitionQuery(message.getContent());
                if (previous.matches().size() == 1) {
                    context = previous.matches().get(0);
                    break;
                }
            }
        }
        if (context.isBlank() || base.contains(context)) {
            return base;
        }
        return base + " " + context;
    }

    private String extractCompetitionFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String[] markers = {"本问题对应赛事为“", "上文已确认的赛事是“", "正式赛事‘"};
        for (String marker : markers) {
            int start = query.indexOf(marker);
            if (start < 0) {
                continue;
            }
            int valueStart = start + marker.length();
            int end = query.indexOf(marker.endsWith("‘") ? "’" : "”", valueStart);
            if (end > valueStart) {
                return query.substring(valueStart, end);
            }
        }
        return "";
    }

    String ensureVisibleAnswer(String answer, String conversationId) {
        if (answer != null && !answer.isBlank()) {
            return answer;
        }
        return emptyAnswerFallback(conversationId);
    }

    private String emptyAnswerFallback(String conversationId) {
        log.warn("Dify returned an empty final answer: conversationId={}", conversationId);
        return "已检索到相关资料，但本次回答生成异常，请重新发送问题。";
    }

    private List<Map<String, Object>> toWebSources(List<WebSearchResultVO> results) {
        return results.stream().map(result -> {
            Map<String, Object> source = new HashMap<>();
            source.put("documentName", result.getTitle());
            source.put("score", result.getScore());
            source.put("content", result.getContent());
            source.put("url", result.getUrl());
            source.put("sourceType", "web");
            return source;
        }).toList();
    }

    private String formatWebAnswer(String query, List<WebSearchResultVO> results) {
        StringBuilder answer = new StringBuilder("### 网络检索结果\n\n");
        answer.append("知识库未检索到可用资料，以下内容来自公开网页，建议以原始网页和官方通知为准。\n\n");
        answer.append("针对“").append(query).append("”，可参考：\n\n");
        for (int i = 0; i < results.size(); i++) {
            WebSearchResultVO result = results.get(i);
            answer.append(i + 1).append(". [").append(result.getTitle()).append("](")
                    .append(result.getUrl()).append(")\n   - ").append(result.getContent()).append("\n\n");
        }
        return answer.toString();
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
        vo.setUserId(conversation.getUserId());
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
