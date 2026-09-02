package com.schoolai.controller;

import com.schoolai.common.BaseController;
import com.schoolai.common.Result;
import com.schoolai.model.dto.ChatSendDTO;
import com.schoolai.model.vo.ConversationVO;
import com.schoolai.model.vo.MessageVO;
import com.schoolai.service.IChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController extends BaseController {

    private final IChatService chatService;

    @PostMapping("/send")
    public void send(@Valid @RequestBody ChatSendDTO chatSendDTO,
                     HttpServletRequest request,
                     HttpServletResponse response) {
        chatService.streamSend(chatSendDTO, request, response);
    }

    @GetMapping("/history")
    public Result<List<ConversationVO>> history(@RequestParam(defaultValue = "30") int days,
                                                 HttpServletRequest request) {
        List<ConversationVO> list = chatService.getHistory(Math.min(Math.max(days, 1), 365), request);
        return success(list);
    }

    @GetMapping("/messages/{conversationId}")
    public Result<List<MessageVO>> messages(@PathVariable String conversationId,
                                             HttpServletRequest request) {
        List<MessageVO> list = chatService.getMessages(conversationId, request);
        return success(list);
    }

    @DeleteMapping("/{conversationId}")
    public Result<Void> delete(@PathVariable String conversationId, HttpServletRequest request) {
        chatService.deleteConversation(conversationId, request);
        return success();
    }
}
