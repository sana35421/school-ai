package com.schoolai.service;

import com.schoolai.model.dto.LoginDTO;
import com.schoolai.model.vo.LoginVO;
import com.schoolai.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

public interface IAuthService {

    LoginVO login(LoginDTO loginDTO);

    LoginVO loginWithYibanTestUser();

    UserVO getCurrentUser(HttpServletRequest request);
}
