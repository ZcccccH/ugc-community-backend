package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.common.utils.TokenUtils;
import com.ugccommunity.backend.dto.request.ResetPasswordRequest;
import com.ugccommunity.backend.dto.request.UserLoginRequest;
import com.ugccommunity.backend.dto.request.UserRegisterRequest;
import com.ugccommunity.backend.entity.User;
import com.ugccommunity.backend.vo.AuthVO;
import com.ugccommunity.backend.vo.UserVO;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserService userService;
    private final TokenUtils tokenUtils;
    private final PasswordService passwordService;

    public AuthService(UserService userService, TokenUtils tokenUtils, PasswordService passwordService) {
        this.userService = userService;
        this.tokenUtils = tokenUtils;
        this.passwordService = passwordService;
    }

    public AuthVO login(UserLoginRequest request) {
        User user = userService.getEntityByLoginAccount(request.getUsername());
        // Registration and password reset trim surrounding whitespace, so login compares the same way (an exact match is still accepted)
        String rawPassword = request.getPassword() == null ? "" : request.getPassword();
        if (user == null || user.getPassword() == null
                || !(passwordService.matches(rawPassword, user.getPassword()) || passwordService.matches(rawPassword.trim(), user.getPassword()))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        userService.upgradePasswordIfNeeded(user, request.getPassword());
        if (!"user".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "移动端仅允许普通用户登录");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被限制，禁止登录");
        }
        userService.markActive(user.getId());
        UserVO userVO = userService.toVO(user, user.getId());
        return new AuthVO(tokenUtils.generateToken(user.getId(), user.getUsername()), userVO);
    }

    public AuthVO register(UserRegisterRequest request) {
        UserVO user = userService.register(request);
        return new AuthVO(tokenUtils.generateToken(user.getId(), user.getUsername()), user);
    }

    public void resetPassword(ResetPasswordRequest request) {
        userService.resetPasswordByBoundContact(request);
    }
}
