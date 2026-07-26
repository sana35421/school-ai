package com.schoolai.utils;

import com.schoolai.entity.User;
import com.schoolai.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserMapper userMapper;

    public User getCurrentUser(HttpServletRequest request) {
        String studentId = (String) request.getAttribute("studentId");
        if (studentId == null) {
            return null;
        }
        return userMapper.findByStudentId(studentId);
    }

    public Long getCurrentUserId(HttpServletRequest request) {
        User user = getCurrentUser(request);
        return user != null ? user.getId() : null;
    }
}