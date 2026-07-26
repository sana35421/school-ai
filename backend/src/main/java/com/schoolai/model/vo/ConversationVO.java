package com.schoolai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationVO {
    private Long id;
    private Long userId;
    private String conversationId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private int messageCount;
}