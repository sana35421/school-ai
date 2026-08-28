package com.schoolai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_attachments")
public class MessageAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long messageId;

    private Long uploadRecordId;

    private LocalDateTime createdAt;
}
