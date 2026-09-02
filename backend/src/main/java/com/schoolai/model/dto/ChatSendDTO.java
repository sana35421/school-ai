package com.schoolai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ChatSendDTO {
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容不能超过4000个字符")
    private String query;
    private String conversationId;
    @Size(max = 4000, message = "赛事选择上下文不能超过4000个字符")
    private String competitionSelectionContext;
    @Size(max = 3, message = "单条消息最多上传3个文件")
    private List<Long> attachmentIds;
}
