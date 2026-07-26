package com.schoolai.model.vo;

import lombok.Data;

@Data
public class KbDocumentVO {
    private Long id;
    private String name;
    private String type;
    private Long size;
    private String status;
    private String uploadedAt;
}
