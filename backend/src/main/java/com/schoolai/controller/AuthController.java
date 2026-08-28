package com.schoolai.controller;

import com.schoolai.common.BaseController;
import com.schoolai.common.Result;
import com.schoolai.model.dto.LoginDTO;
import com.schoolai.model.vo.LoginVO;
import com.schoolai.model.vo.UserVO;
import com.schoolai.service.IAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final IAuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        LoginVO loginVO = authService.login(loginDTO);
        return success(loginVO);
    }

    @PostMapping("/yiban/login")
    public Result<LoginVO> yibanLogin() {
        return success(authService.loginWithYibanTestUser());
    }

    @GetMapping("/me")
    public Result<UserVO> me(HttpServletRequest request) {
        UserVO userVO = authService.getCurrentUser(request);
        return success(userVO);
    }
}
