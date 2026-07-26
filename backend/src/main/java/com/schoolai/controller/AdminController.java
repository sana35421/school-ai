package com.schoolai.controller;

import com.schoolai.common.BaseController;
import com.schoolai.common.Result;
import com.schoolai.model.vo.ConversationVO;
import com.schoolai.model.vo.KbDocumentVO;
import com.schoolai.model.vo.MessageVO;
import com.schoolai.model.vo.StudentOverviewVO;
import com.schoolai.service.IAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController extends BaseController {

    private final IAdminService adminService;

    @GetMapping("/class/students")
    public Result<List<StudentOverviewVO>> classStudents(HttpServletRequest request) {
        List<StudentOverviewVO> list = adminService.getClassStudents(request);
        return success(list);
    }

    @GetMapping("/class/conversations")
    public Result<List<ConversationVO>> classConversations(@RequestParam(required = false) Long studentId,
                                                            @RequestParam(defaultValue = "7") int days,
                                                            HttpServletRequest request) {
        List<ConversationVO> list = adminService.getClassConversations(studentId, days, request);
        return success(list);
    }

    @GetMapping("/class/messages/{conversationId}")
    public Result<List<MessageVO>> classMessages(@PathVariable String conversationId,
                                                  HttpServletRequest request) {
        List<MessageVO> list = adminService.getClassMessages(conversationId, request);
        return success(list);
    }

    @GetMapping("/kb/documents")
    public Result<List<KbDocumentVO>> kbDocuments(HttpServletRequest request) {
        return success(adminService.getKbDocuments(request));
    }

    @PostMapping(value = "/kb/documents", consumes = "multipart/form-data")
    public Result<KbDocumentVO> uploadKbDocument(@RequestPart("file") MultipartFile file,
                                                  HttpServletRequest request) {
        return success(adminService.uploadKbDocument(file, request));
    }

    @DeleteMapping("/kb/documents/{documentId}")
    public Result<Void> deleteKbDocument(@PathVariable Long documentId, HttpServletRequest request) {
        adminService.deleteKbDocument(documentId, request);
        return success();
    }
}
