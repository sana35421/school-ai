package com.schoolai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DifyChatResult {
    private String conversationId;
    private String answer;
    private String sourcesJson;
}
