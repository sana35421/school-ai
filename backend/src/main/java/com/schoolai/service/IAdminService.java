package com.schoolai.service;

import com.schoolai.model.vo.ConversationVO;
import com.schoolai.model.vo.KbDocumentVO;
import com.schoolai.model.vo.MessageVO;
import com.schoolai.model.vo.StudentOverviewVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IAdminService {

    List<StudentOverviewVO> getClassStudents(HttpServletRequest request);

    List<ConversationVO> getClassConversations(Long studentUserId, int days, HttpServletRequest request);

    List<MessageVO> getClassMessages(String conversationId, HttpServletRequest request);

    List<KbDocumentVO> getKbDocuments(HttpServletRequest request);

    KbDocumentVO uploadKbDocument(MultipartFile file, HttpServletRequest request);

    void deleteKbDocument(Long documentId, HttpServletRequest request);
}
