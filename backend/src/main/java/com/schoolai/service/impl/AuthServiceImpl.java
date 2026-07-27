package com.schoolai.service.impl;

import com.schoolai.common.exception.ServiceException;
import com.schoolai.entity.User;
import com.schoolai.enums.UserRole;
import com.schoolai.mapper.UserMapper;
import com.schoolai.model.dto.LoginDTO;
import com.schoolai.model.vo.LoginVO;
import com.schoolai.model.vo.UserVO;
import com.schoolai.security.JwtUtil;
import com.schoolai.service.IAuthService;
import com.schoolai.service.LoginSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginSessionService loginSessionService;

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.findByStudentId(dto.getStudentId());
        if (user == null) {
            throw new ServiceException(401, "学号或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ServiceException(401, "学号或密码错误");
        }
        String sessionId = UUID.randomUUID().toString();
        loginSessionService.replaceSession(user.getId(), sessionId, Duration.ofDays(7));
        String token = jwtUtil.generate(user.getId(), user.getStudentId(), user.getRole(), sessionId);

        UserVO userVO = convertToUserVO(user);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(userVO);
        return loginVO;
    }

    @Override
    public UserVO getCurrentUser(HttpServletRequest request) {
        String studentId = (String) request.getAttribute("studentId");
        if (studentId == null) {
            throw new ServiceException(401, "未登录");
        }
        User user = userMapper.findByStudentId(studentId);
        if (user == null) {
            throw new ServiceException(401, "用户不存在");
        }
        return convertToUserVO(user);
    }

    private UserVO convertToUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setStudentId(user.getStudentId());
        vo.setRealName(user.getRealName());
        vo.setClassId(user.getClassId());
        vo.setRole(user.getRole());
        vo.setRoleLabel(UserRole.fromCode(user.getRole()).getLabel());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}