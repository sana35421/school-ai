package com.schoolai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
    SUPER_ADMIN("super_admin", "超级管理员"),
    ADMIN("admin", "教务管理员"),
    COUNSELOR("counselor", "辅导员"),
    STUDENT("student", "学生");

    private final String code;
    private final String label;

    public boolean isStaff() {
        return this == COUNSELOR || this == ADMIN || this == SUPER_ADMIN;
    }

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    public static UserRole fromCode(String code) {
        if (code == null) {
            return STUDENT;
        }
        for (UserRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的用户角色: " + code);
    }
}