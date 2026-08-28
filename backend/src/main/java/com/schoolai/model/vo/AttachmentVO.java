package com.schoolai.model.vo;

import lombok.Data;

@Data
public class AttachmentVO {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String status;
}
