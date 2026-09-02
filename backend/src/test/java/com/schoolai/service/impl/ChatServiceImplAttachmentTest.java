package com.schoolai.service.impl;

import com.schoolai.common.exception.ServiceException;
import com.schoolai.entity.Message;
import com.schoolai.mapper.MessageMapper;
import com.schoolai.entity.UploadRecord;
import com.schoolai.mapper.UploadRecordMapper;
import com.schoolai.service.WebSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplAttachmentTest {

    @Mock
    private UploadRecordMapper uploadRecordMapper;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private WebSearchService webSearchService;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Test
    void resolveAttachments_shouldRejectDuplicateIds() {
        List<Long> duplicateIds = List.of(1L, 1L);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> chatService.resolveAttachments(duplicateIds, 100L));
        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("不能重复添加同一个附件"));
    }

    @Test
    void resolveAttachments_shouldRejectNonExistentId() {
        List<Long> ids = List.of(999L);
        when(uploadRecordMapper.selectBatchIds(ids)).thenReturn(List.of());

        ServiceException exception = assertThrows(ServiceException.class,
                () -> chatService.resolveAttachments(ids, 100L));
        assertEquals(403, exception.getCode());
        assertTrue(exception.getMessage().contains("附件不存在或无权访问"));
    }

    @Test
    void resolveAttachments_shouldRejectOtherUsersAttachment() {
        List<Long> ids = List.of(1L);
        UploadRecord otherUserRecord = new UploadRecord();
        otherUserRecord.setId(1L);
        otherUserRecord.setUserId(200L);
        otherUserRecord.setStatus("uploaded");
        otherUserRecord.setDifyFileId("dify-123");

        when(uploadRecordMapper.selectBatchIds(ids)).thenReturn(List.of(otherUserRecord));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> chatService.resolveAttachments(ids, 100L));
        assertEquals(403, exception.getCode());
    }

    @Test
    void resolveAttachments_shouldRejectIncompleteUpload() {
        List<Long> ids = List.of(1L);
        UploadRecord pendingRecord = new UploadRecord();
        pendingRecord.setId(1L);
        pendingRecord.setUserId(100L);
        pendingRecord.setStatus("uploading");
        pendingRecord.setDifyFileId(null);

        when(uploadRecordMapper.selectBatchIds(ids)).thenReturn(List.of(pendingRecord));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> chatService.resolveAttachments(ids, 100L));
        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("附件尚未上传完成"));
    }

    @Test
    void resolveAttachments_shouldAcceptValidOwnAttachment() {
        List<Long> ids = List.of(1L);
        UploadRecord validRecord = new UploadRecord();
        validRecord.setId(1L);
        validRecord.setUserId(100L);
        validRecord.setStatus("uploaded");
        validRecord.setDifyFileId("dify-123");

        when(uploadRecordMapper.selectBatchIds(ids)).thenReturn(List.of(validRecord));

        List<UploadRecord> result = chatService.resolveAttachments(ids, 100L);
        assertEquals(1, result.size());
        assertEquals("dify-123", result.get(0).getDifyFileId());
    }

    @Test
    void shouldUseWebFallback_whenDifyExplicitlyReportsNoKnowledgeMatch() {
        when(webSearchService.isAvailable()).thenReturn(true);

        assertTrue(chatService.shouldUseWebFallback(
                "知识库中暂时没有这方面的具体信息。你可以指定比赛方向，我再为你匹配。"));
    }

    @Test
    void shouldNotUseWebFallback_whenDifyReturnsKnowledgeAnswer() {
        when(webSearchService.isAvailable()).thenReturn(true);

        assertFalse(chatService.shouldUseWebFallback("蓝桥杯通常在每年四月举行省赛。"));
    }

    @Test
    void shouldUseWebFallback_whenDifyReportsMissingTeammateRecord() {
        when(webSearchService.isAvailable()).thenReturn(true);

        assertTrue(chatService.shouldUseWebFallback("知识库中暂时没有易班数字校园项目的完整具体队友记录。"));
    }

    @Test
    void buildWebSearchQuery_shouldKeepPreviousCompetitionContextForFollowUp() {
        Message previousUserMessage = new Message();
        previousUserMessage.setRole("user");
        previousUserMessage.setContent("易班");
        when(messageMapper.findByConversationId("web-conversation")).thenReturn(List.of(previousUserMessage));

        String result = chatService.buildWebSearchQuery("最新通知", "最新通知", "web-conversation");

        assertEquals("最新通知 易班数字校园项目", result);
    }

    @Test
    void resolveCompetitionQuery_shouldMapYibanAliasToDigitalCampusProject() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("易班比赛推荐老师和队友");

        assertEquals(List.of("易班数字校园项目"), result.matches());
        assertTrue(result.query().contains("易班数字校园项目"));
        assertTrue(result.query().contains("联合推荐"));
        assertFalse(result.query().contains("只检索该赛事"));
        assertFalse(result.requiresSelection());
    }

    @Test
    void resolveCompetitionQuery_shouldIncludeYibanTeacherRecordsForJointRetrieval() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("易班比赛推荐老师和队友");

        assertTrue(result.query().contains("陈致远老师"));
        assertTrue(result.query().contains("梁星语老师"));
        assertTrue(result.query().contains("000-0103-1001"));
        assertTrue(result.query().contains("工作邮箱"));
    }

    @Test
    void resolveCompetitionQuery_shouldAskForSelectionWhenSeveralCompetitionsMatch() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("ICPC和美赛哪个更适合我");

        assertEquals(List.of("算法与程序设计竞赛", "美国大学生数学建模竞赛（MCM/ICM）"), result.matches());
        assertTrue(result.requiresSelection());
    }

    @Test
    void resolveCompetitionQuery_shouldAskForSelectionForAmbiguousEnglishCompetition() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("英语竞赛");

        assertEquals(List.of("全国大学生英语竞赛", "外研社·国才杯英语挑战赛"), result.matches());
        assertTrue(result.requiresSelection());
    }

    @Test
    void resolveCompetitionQuery_shouldShowDirectoryForGenericCompetitionQuery() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("学科竞赛");

        assertTrue(result.requiresDirectorySelection());
        assertFalse(result.requiresSelection());
    }

    @Test
    void resolveCompetitionQuery_shouldRecommendPeopleAfterDirectorySelection() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery(
                "软件产品与创新创业", "学科竞赛目录：推荐所选方向的指导老师和队友");

        assertFalse(result.requiresDirectorySelection());
        assertFalse(result.requiresSelection());
        assertEquals(List.of("软件产品与创新创业"), result.matches());
        assertTrue(result.query().contains("老师与队友联合查询"));
    }

    @Test
    void resolveCompetitionQuery_shouldRetainRecommendationIntentAfterEnglishCompetitionSelection() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery(
                "全国大学生英语竞赛", "推荐英语竞赛队友");

        assertFalse(result.requiresSelection());
        assertEquals(List.of("全国大学生英语竞赛"), result.matches());
        assertTrue(result.query().contains("推荐英语竞赛队友"));
        assertTrue(result.query().contains("具体队友记录"));
    }

    @Test
    void resolveCompetitionQuery_shouldMapNationalMathematicsCompetition() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("全国大学生数学竞赛");

        assertEquals(List.of("全国大学生数学竞赛"), result.matches());
        assertTrue(result.query().contains("全国大学生数学竞赛"));
    }

    @Test
    void resolveCompetitionQuery_shouldReturnCompetitionOnlyForGenericCompetitionQuestion() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("全国大学生数学竞赛");

        assertTrue(result.query().contains("这是赛事简介查询"));
        assertTrue(result.query().contains("不要推荐指导老师或队友"));
        assertFalse(result.query().contains("联合查询"));
    }

    @Test
    void resolveCompetitionQuery_shouldLimitTeacherAndTeammateQueriesSeparately() {
        ChatServiceImpl.CompetitionQueryResolution teacher =
                chatService.resolveCompetitionQuery("全国大学生数学竞赛指导老师");
        ChatServiceImpl.CompetitionQueryResolution teammate =
                chatService.resolveCompetitionQuery("全国大学生数学竞赛推荐队友");

        assertTrue(teacher.query().contains("只返回该赛事的完整指导老师记录"));
        assertTrue(teacher.query().contains("不要输出队友"));
        assertTrue(teammate.query().contains("只返回该赛事的完整具体队友记录"));
        assertTrue(teammate.query().contains("不要输出指导老师"));
    }

    @Test
    void resolveCompetitionQuery_shouldMapInnovationCompetitionAliasesToCanonicalDocument() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("推荐大学生创业大赛的队友");

        assertEquals(List.of("中国国际大学生创新大赛"), result.matches());
        assertTrue(result.query().contains("中国国际大学生创新大赛"));
    }

    @Test
    void resolveCompetitionQuery_shouldMapCommonInnovationCompetitionNames() {
        assertEquals(List.of("中国国际大学生创新大赛"),
                chatService.resolveCompetitionQuery("大学生创新创业大赛").matches());
        assertEquals(List.of("中国国际大学生创新大赛"),
                chatService.resolveCompetitionQuery("互联网+项目").matches());
    }

    @Test
    void resolveCompetitionQuery_shouldKeepGenericSoftwareAliasesSeparateFromInnovationCompetition() {
        ChatServiceImpl.CompetitionQueryResolution result = chatService.resolveCompetitionQuery("推荐软件创新竞赛队友");

        assertEquals(List.of("软件开发与创新项目竞赛"), result.matches());
        assertFalse(result.query().contains("中国国际大学生创新大赛"));
    }

    @Test
    void resolveCompetitionQuery_shouldRecognizeAliasesAcrossCompetitionCategories() {
        assertEquals(List.of("算法与程序设计竞赛"),
                chatService.resolveCompetitionQuery("ICPC程序设计大赛").matches());
        assertEquals(List.of("网络安全竞赛"),
                chatService.resolveCompetitionQuery("CISCN网络安全竞赛").matches());
        assertEquals(List.of("机器人与智能硬件竞赛"),
                chatService.resolveCompetitionQuery("全国大学生电子设计竞赛").matches());
        assertEquals(List.of("科研建模与数据分析"),
                chatService.resolveCompetitionQuery("正大杯市场调查大赛").matches());
        assertEquals(List.of("传播表达与视觉设计"),
                chatService.resolveCompetitionQuery("大广赛").matches());
    }

    @Test
    void enrichCompetitionFollowUp_shouldCarryPreviousCompetitionIntoTeammateQuery() {
        Message previousUserMessage = new Message();
        previousUserMessage.setRole("user");
        previousUserMessage.setContent("全国大学生数学竞赛");
        when(messageMapper.findByConversationId("math-conversation")).thenReturn(List.of(previousUserMessage));

        String result = chatService.enrichCompetitionFollowUp("推荐一点队友", "math-conversation");

        assertTrue(result.contains("全国大学生数学竞赛"));
        assertTrue(result.contains("完整队友记录"));
    }

    @Test
    void enrichCompetitionFollowUp_shouldRequestTeachersAndTeammatesTogether() {
        Message previousUserMessage = new Message();
        previousUserMessage.setRole("user");
        previousUserMessage.setContent("易班数字校园项目");
        when(messageMapper.findByConversationId("yiban-conversation")).thenReturn(List.of(previousUserMessage));

        String result = chatService.enrichCompetitionFollowUp("推荐一点队友和指导老师", "yiban-conversation");

        assertTrue(result.contains("完整指导老师记录"));
        assertTrue(result.contains("必须同时检索"));
        assertTrue(result.contains("顾清越"));
        assertTrue(result.contains("顾思涵"));
    }

    @Test
    void shouldReturnVisibleFallbackWhenDifyAnswerIsBlank() {
        String answer = chatService.ensureVisibleAnswer("", "test-conversation");

        assertEquals("已检索到相关资料，但本次回答生成异常，请重新发送问题。", answer);
    }

    @Test
    void shouldNotUseWebFallbackWhenKnowledgeBaseReturnedPartialSources() {
        when(webSearchService.isAvailable()).thenReturn(true);

        assertFalse(chatService.shouldUseWebFallback(
                "知识库中暂时没有该赛事的赛制和参赛要求具体信息。",
                "[{\"documentName\":\"中国国际大学生创新大赛.md\",\"score\":0.91}]"));
        assertFalse(chatService.shouldUseWebFallback(
                "知识库中暂时没有该赛事的赛制和参赛要求具体信息。", "[]"));
    }

}
