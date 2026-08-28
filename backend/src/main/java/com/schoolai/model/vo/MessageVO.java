package com.schoolai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MessageVO {
    private Long id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
    private List<SourceItemVO> sources;
    private List<AttachmentVO> attachments;
}
