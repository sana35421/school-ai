package com.schoolai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_documents")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String storagePath;

    private String status;

    private String difyDocId;

    private Long uploadedBy;

    private LocalDateTime uploadedAt;
}