package com.schoolai.service.impl;

import com.schoolai.common.exception.ServiceException;
import com.schoolai.config.YibanLoginConfig;
import com.schoolai.entity.User;
import com.schoolai.mapper.UserMapper;
import com.schoolai.model.vo.LoginVO;
import com.schoolai.security.JwtUtil;
import com.schoolai.service.LoginSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private LoginSessionService loginSessionService;

    private YibanLoginConfig config;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        config = new YibanLoginConfig();
        config.setEnabled(true);
        config.setTestStudentId("yiban_test");
        authService = new AuthServiceImpl(userMapper, passwordEncoder, jwtUtil, loginSessionService, config);
    }

    @Test
    void loginWithYibanTestUser_shouldLoginConfiguredStudent() {
        User user = new User();
        user.setId(10L);
        user.setStudentId("yiban_test");
        user.setRealName("易班测试用户");
        user.setRole("student");
        when(userMapper.findByStudentId("yiban_test")).thenReturn(user);
        when(jwtUtil.generate(any(), anyString(), anyString(), anyString())).thenReturn("jwt-token");

        LoginVO result = authService.loginWithYibanTestUser();

        assertEquals("jwt-token", result.getToken());
        assertEquals("yiban_test", result.getUser().getStudentId());
        verify(loginSessionService).replaceSession(any(), anyString(), any());
    }

    @Test
    void loginWithYibanTestUser_shouldRejectWhenDisabled() {
        config.setEnabled(false);

        ServiceException exception = assertThrows(ServiceException.class, authService::loginWithYibanTestUser);

        assertEquals(503, exception.getCode());
        verify(userMapper, never()).findByStudentId(anyString());
    }

    @Test
    void loginWithYibanTestUser_shouldRejectNonStudentAccount() {
        User user = new User();
        user.setRole("admin");
        when(userMapper.findByStudentId("yiban_test")).thenReturn(user);

        ServiceException exception = assertThrows(ServiceException.class, authService::loginWithYibanTestUser);

        assertEquals(500, exception.getCode());
        verify(loginSessionService, never()).replaceSession(any(), anyString(), any());
    }
}
