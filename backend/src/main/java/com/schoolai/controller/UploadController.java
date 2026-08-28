package com.schoolai.controller;

import com.schoolai.common.BaseController;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.schoolai.common.Result;
import com.schoolai.common.exception.ServiceException;
import com.schoolai.entity.UploadRecord;
import com.schoolai.entity.User;
import com.schoolai.mapper.UploadRecordMapper;
import com.schoolai.model.dto.UploadRecordDTO;
import com.schoolai.model.vo.DifyUploadedFileVO;
import com.schoolai.model.vo.UploadRecordVO;
import com.schoolai.service.DifyClient;
import com.schoolai.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController extends BaseController {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md", "csv", "xlsx");

    private final SecurityUtils securityUtils;
    private final UploadRecordMapper uploadRecordMapper;
    private final DifyClient difyClient;

    @Value("${chat.file-upload-enabled:true}")
    private boolean fileUploadEnabled;

    @PostMapping("/file")
    public Result<UploadRecordVO> upload(@RequestPart("file") MultipartFile file,
                                         HttpServletRequest request) {
        if (!fileUploadEnabled) {
            throw new ServiceException(503, "文件上传功能暂未开放");
        }
        User user = securityUtils.getCurrentUser(request);
        if (user == null) {
            throw new ServiceException(401, "未登录");
        }
        long todayCount = uploadRecordMapper.selectCount(
                new LambdaQueryWrapper<UploadRecord>()
                        .eq(UploadRecord::getUserId, user.getId())
                        .eq(UploadRecord::getStatus, "uploaded")
                        .ge(UploadRecord::getCreatedAt, LocalDate.now().atStartOfDay()));
        if (todayCount >= 20) {
            throw new ServiceException(429, "今日文件上传次数已达上限");
        }
        validateFile(file);
        try {
            difyClient.ensureDocumentUploadEnabled(user.getStudentId(), file.getOriginalFilename(), file.getSize());
            DifyUploadedFileVO difyFile = difyClient.uploadFile(file, user.getStudentId());
            UploadRecord record = new UploadRecord();
            record.setUserId(user.getId());
            record.setFileName(file.getOriginalFilename());
            record.setFileType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            record.setFileSize(file.getSize());
            record.setDifyFileId(difyFile.getId());
            record.setStatus("uploaded");
            record.setCreatedAt(LocalDateTime.now());
            uploadRecordMapper.insert(record);
            return success(toVO(record));
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("未启用文档上传能力")) {
                throw new ServiceException(503, "当前智能助手尚未启用文档读取，请先在Dify应用中开启文件上传");
            }
            if (e.getMessage() != null && e.getMessage().contains("不允许该文件格式或大小")) {
                throw new ServiceException(400, "当前智能助手不支持该文件格式或大小");
            }
            throw new ServiceException(502, "文件上传到智能助手失败，请稍后重试");
        }
    }

    @PostMapping("/record")
    public Result<UploadRecordVO> record(@RequestBody UploadRecordDTO dto,
                                          HttpServletRequest request) {
        Long userId = securityUtils.getCurrentUserId(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        UploadRecord record = new UploadRecord();
        record.setUserId(userId);
        record.setFileName(dto.getFileName());
        record.setFileType(dto.getFileType());
        record.setFileSize(dto.getFileSize() != null ? dto.getFileSize() : 0L);
        record.setStatus("metadata_only");
        record.setCreatedAt(LocalDateTime.now());
        uploadRecordMapper.insert(record);
        return success(toVO(record));
    }

    void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException(400, "请选择非空文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException(400, "单个文件不能超过10MB");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            throw new ServiceException(400, "不支持该文件格式");
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ServiceException(400, "仅支持PDF、DOCX、TXT、MD、CSV和XLSX文件");
        }
    }

    private UploadRecordVO toVO(UploadRecord record) {
        UploadRecordVO vo = new UploadRecordVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setFileType(record.getFileType());
        vo.setFileSize(record.getFileSize());
        vo.setStatus(record.getStatus());
        vo.setCreatedAt(record.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return vo;
    }
}
