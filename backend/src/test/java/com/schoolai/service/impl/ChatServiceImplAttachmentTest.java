package com.schoolai.service.impl;

import com.schoolai.common.exception.ServiceException;
import com.schoolai.entity.UploadRecord;
import com.schoolai.mapper.UploadRecordMapper;
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
}
