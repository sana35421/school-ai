package com.schoolai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.schoolai.common.exception.ServiceException;
import com.schoolai.entity.Conversation;
import com.schoolai.entity.KbDocument;
import com.schoolai.entity.Message;
import com.schoolai.entity.User;
import com.schoolai.enums.UserRole;
import com.schoolai.mapper.ConversationMapper;
import com.schoolai.mapper.KbDocumentMapper;
import com.schoolai.mapper.MessageMapper;
import com.schoolai.mapper.UserMapper;
import com.schoolai.model.vo.ConversationVO;
import com.schoolai.model.vo.KbDocumentVO;
import com.schoolai.model.vo.MessageVO;
import com.schoolai.model.vo.StudentOverviewVO;
import com.schoolai.service.IAdminService;
import com.schoolai.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements IAdminService {

    private final UserMapper userMapper;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final SecurityUtils securityUtils;
    private final KbDocumentMapper kbDocumentMapper;

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Path UPLOAD_ROOT = Paths.get(System.getProperty("user.dir"), "data", "knowledge-base")
            .toAbsolutePath().normalize();

    @Override
    public List<StudentOverviewVO> getClassStudents(HttpServletRequest request) {
        User currentUser = securityUtils.getCurrentUser(request);
        if (currentUser == null) {
            throw new ServiceException(401, "未登录");
        }

        UserRole role = UserRole.fromCode(currentUser.getRole());
        if (!role.isStaff()) {
            throw new ServiceException(403, "无权访问");
        }

        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("role", UserRole.STUDENT.getCode());

        if (!role.isAdmin()) {
            if (currentUser.getClassId() == null) {
                return Collections.emptyList();
            }
            userQuery.eq("class_id", currentUser.getClassId());
        }

        List<User> students = userMapper.selectList(userQuery);
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        List<StudentOverviewVO> result = new ArrayList<>();
        for (User student : students) {
            QueryWrapper<Conversation> convQuery = new QueryWrapper<>();
            convQuery.eq("user_id", student.getId())
                    .ge("last_active_at", since);

            List<Conversation> conversations = conversationMapper.selectList(convQuery);
            int totalMessages = 0;
            for (Conversation conv : conversations) {
                List<Message> msgs = messageMapper.findByConversationId(conv.getConversationId());
                totalMessages += msgs != null ? msgs.size() : 0;
            }

            StudentOverviewVO vo = new StudentOverviewVO();
            vo.setId(student.getId());
            vo.setStudentId(student.getStudentId());
            vo.setRealName(student.getRealName());
            vo.setConversationCount(conversations.size());
            vo.setMessageCount(totalMessages);
            vo.setLastActiveAt(conversations.isEmpty() ? null
                    : conversations.get(0).getLastActiveAt().toString());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<ConversationVO> getClassConversations(Long studentUserId, int days, HttpServletRequest request) {
        User currentUser = securityUtils.getCurrentUser(request);
        if (currentUser == null) {
            throw new ServiceException(401, "未登录");
        }

        UserRole role = UserRole.fromCode(currentUser.getRole());
        if (!role.isStaff()) {
            throw new ServiceException(403, "无权访问");
        }

        LocalDateTime since = LocalDateTime.now().minusDays(days);
        QueryWrapper<Conversation> query = new QueryWrapper<>();
        query.ge("last_active_at", since);

        List<Long> targetUserIds = resolveTargetUserIds(currentUser, role, studentUserId);
        if (targetUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        query.in("user_id", targetUserIds);
        query.orderByDesc("last_active_at");

        List<Conversation> conversations = conversationMapper.selectList(query);

        return conversations.stream().map(conv -> {
            ConversationVO vo = new ConversationVO();
            vo.setId(conv.getId());
            vo.setUserId(conv.getUserId());
            vo.setConversationId(conv.getConversationId());
            vo.setTitle(conv.getTitle());
            vo.setCreatedAt(conv.getCreatedAt());
            vo.setLastActiveAt(conv.getLastActiveAt());
            List<Message> msgs = messageMapper.findByConversationId(conv.getConversationId());
            vo.setMessageCount(msgs != null ? msgs.size() : 0);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<MessageVO> getClassMessages(String conversationId, HttpServletRequest request) {
        User currentUser = securityUtils.getCurrentUser(request);
        if (currentUser == null) {
            throw new ServiceException(401, "未登录");
        }

        UserRole role = UserRole.fromCode(currentUser.getRole());
        if (!role.isStaff()) {
            throw new ServiceException(403, "无权访问");
        }

        Conversation conv = conversationMapper.findByConversationId(conversationId);
        if (conv == null) {
            throw new ServiceException(404, "对话不存在");
        }

        if (!role.isAdmin()) {
            User owner = userMapper.selectById(conv.getUserId());
            if (owner == null || !Objects.equals(owner.getClassId(), currentUser.getClassId())) {
                throw new ServiceException(403, "无权查看该对话");
            }
        }

        List<Message> messages = messageMapper.findByConversationId(conversationId);
        return messages.stream().map(this::convertToMessageVO).collect(Collectors.toList());
    }

    private List<Long> resolveTargetUserIds(User currentUser, UserRole role, Long studentUserId) {
        if (role.isAdmin()) {
            if (studentUserId != null) {
                User target = userMapper.selectById(studentUserId);
                if (target == null) {
                    return Collections.emptyList();
                }
                return Collections.singletonList(target.getId());
            }
            QueryWrapper<User> userQuery = new QueryWrapper<>();
            userQuery.eq("role", UserRole.STUDENT.getCode());
            List<User> allStudents = userMapper.selectList(userQuery);
            return allStudents.stream().map(User::getId).collect(Collectors.toList());
        }

        if (currentUser.getClassId() == null) {
            return Collections.emptyList();
        }

        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("class_id", currentUser.getClassId())
                .eq("role", UserRole.STUDENT.getCode());

        if (studentUserId != null) {
            userQuery.eq("id", studentUserId);
        }

        List<User> classStudents = userMapper.selectList(userQuery);
        return classStudents.stream().map(User::getId).collect(Collectors.toList());
    }

    @Override
    public List<KbDocumentVO> getKbDocuments(HttpServletRequest request) {
        requireAdmin(request);
        return kbDocumentMapper.selectAllOrdered().stream().map(this::toKbDocumentVO).collect(Collectors.toList());
    }

    @Override
    public KbDocumentVO uploadKbDocument(MultipartFile file, HttpServletRequest request) {
        User currentUser = requireAdmin(request);
        if (file == null || file.isEmpty()) {
            throw new ServiceException(400, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException(400, "文件大小不能超过 50MB");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new ServiceException(400, "文件名不能为空");
        }
        String cleanName = Paths.get(originalName).getFileName().toString();
        String extension = getExtension(cleanName);
        if (!List.of("pdf", "txt", "md", "doc", "docx", "ppt", "pptx").contains(extension)) {
            throw new ServiceException(400, "仅支持 PDF、TXT、MD、DOC、DOCX、PPT、PPTX 文件");
        }

        KbDocument document = new KbDocument();
        document.setFileName(cleanName);
        document.setFileType(file.getContentType() != null ? file.getContentType() : extension);
        document.setFileSize(file.getSize());
        document.setStoragePath("");
        document.setStatus("completed");
        document.setDifyDocId("");
        document.setUploadedBy(currentUser.getId());
        document.setUploadedAt(LocalDateTime.now());
        kbDocumentMapper.insert(document);
        return toKbDocumentVO(document);
    }

    @Override
    public void deleteKbDocument(Long documentId, HttpServletRequest request) {
        requireAdmin(request);
        if (documentId == null) {
            throw new ServiceException(404, "文档不存在");
        }
        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new ServiceException(404, "文档不存在");
        }
        try {
            if (document.getStoragePath() != null && !document.getStoragePath().isBlank()) {
                Files.deleteIfExists(Paths.get(document.getStoragePath()).normalize());
            }
            kbDocumentMapper.deleteById(documentId);
        } catch (IOException e) {
            log.error("删除知识库文件失败: {}", documentId, e);
            throw new ServiceException(500, "文件删除失败，请稍后重试");
        }
    }

    private User requireAdmin(HttpServletRequest request) {
        User currentUser = securityUtils.getCurrentUser(request);
        if (currentUser == null) {
            throw new ServiceException(401, "未登录");
        }
        if (!UserRole.fromCode(currentUser.getRole()).isAdmin()) {
            throw new ServiceException(403, "仅管理员可以管理知识库");
        }
        return currentUser;
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
    }

    private KbDocumentVO toKbDocumentVO(KbDocument document) {
        KbDocumentVO vo = new KbDocumentVO();
        vo.setId(document.getId());
        vo.setName(document.getFileName());
        vo.setType(document.getFileType());
        vo.setSize(document.getFileSize() == null ? 0L : document.getFileSize());
        vo.setStatus(document.getStatus() == null || document.getStatus().isBlank() ? "pending" : document.getStatus());
        vo.setUploadedAt(document.getUploadedAt() == null ? "" : document.getUploadedAt().toString());
        return vo;
    }

    private MessageVO convertToMessageVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }
}