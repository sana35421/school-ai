package com.schoolai.service;

import com.schoolai.model.dto.ChatSendDTO;
import com.schoolai.model.vo.ConversationVO;
import com.schoolai.model.vo.MessageVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface IChatService {

    void streamSend(ChatSendDTO dto, HttpServletRequest request, HttpServletResponse response);

    List<ConversationVO> getHistory(int days, HttpServletRequest request);

    List<MessageVO> getMessages(String conversationId, HttpServletRequest request);

    void deleteConversation(String conversationId, HttpServletRequest request);
}