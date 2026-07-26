package com.schoolai.model.vo;

import lombok.Data;

@Data
public class StudentOverviewVO {
    private Long id;
    private String studentId;
    private String realName;
    private int conversationCount;
    private int messageCount;
    private String lastActiveAt;
}