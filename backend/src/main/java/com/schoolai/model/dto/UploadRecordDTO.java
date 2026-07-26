package com.schoolai.model.dto;

import lombok.Data;

@Data
public class UploadRecordDTO {
    private String fileName;
    private String fileType;
    private Long fileSize;
}
