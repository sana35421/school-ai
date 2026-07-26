package com.schoolai.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageRole {
    USER("user"),
    ASSISTANT("assistant");
    private final String code;
}