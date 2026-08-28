package com.schoolai.controller;

import com.schoolai.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadControllerTest {

    private final UploadController uploadController = new UploadController(null, null, null);

    @Test
    void validateFile_shouldRejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> uploadController.validateFile(emptyFile));
        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("请选择非空文件"));
    }

    @Test
    void validateFile_shouldRejectOversizedFile() {
        byte[] largeContent = new byte[11 * 1024 * 1024];
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.pdf", "application/pdf", largeContent);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> uploadController.validateFile(largeFile));
        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("不能超过10MB"));
    }

    @Test
    void validateFile_shouldRejectDisallowedExtension() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "virus.exe", "application/x-msdownload", "test".getBytes());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> uploadController.validateFile(exeFile));
        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("仅支持PDF、DOCX、TXT、MD、CSV和XLSX"));
    }

    @Test
    void validateFile_shouldAcceptValidPdf() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF content".getBytes());
        assertDoesNotThrow(() -> uploadController.validateFile(pdfFile));
    }

    @Test
    void validateFile_shouldRejectMissingExtension() {
        MockMultipartFile noExtFile = new MockMultipartFile(
                "file", "noextension", "application/octet-stream", "content".getBytes());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> uploadController.validateFile(noExtFile));
        assertEquals(400, exception.getCode());
    }
}
