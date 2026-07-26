package com.schoolai.controller;

import com.schoolai.common.BaseController;
import com.schoolai.common.Result;
import com.schoolai.entity.UploadRecord;
import com.schoolai.mapper.UploadRecordMapper;
import com.schoolai.model.dto.UploadRecordDTO;
import com.schoolai.model.vo.UploadRecordVO;
import com.schoolai.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController extends BaseController {

    private final SecurityUtils securityUtils;
    private final UploadRecordMapper uploadRecordMapper;

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
        record.setCreatedAt(LocalDateTime.now());
        uploadRecordMapper.insert(record);

        UploadRecordVO vo = new UploadRecordVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setFileType(record.getFileType());
        vo.setFileSize(record.getFileSize());
        vo.setCreatedAt(record.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return success(vo);
    }
}
