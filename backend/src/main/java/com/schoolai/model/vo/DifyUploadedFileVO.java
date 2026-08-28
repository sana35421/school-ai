package com.schoolai.model.vo;

import lombok.Data;

@Data
public class DifyUploadedFileVO {
    private String id;
    private String name;
    private Long size;
    private String mimeType;
}
