package com.schoolai.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String studentId;
    private String realName;
    private Long classId;
    private String role;
    private String roleLabel;
    private LocalDateTime createdAt;
}