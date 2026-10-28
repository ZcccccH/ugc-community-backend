package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.ResetPasswordRequest;
import com.ugccommunity.backend.dto.request.UserLoginRequest;
import com.ugccommunity.backend.dto.request.UserRegisterRequest;
import com.ugccommunity.backend.service.AuthService;
import com.ugccommunity.backend.service.RateLimitService;
import com.ugccommunity.backend.vo.AuthVO;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final CsrfTokenController csrfTokenController;

    /**
     * Password reset by username + bound phone + bound email has no one-time code,
     * so anyone who knows those three values could take over the account.
     * Disabled by default; enable only after adding an OTP / email verification step.
     */
    @org.springframework.beans.factory.annotation.Value("${app.auth.contact-reset-enabled:false}")
    private boolean contactResetEnabled;

    public AuthController(AuthService authService, RateLimitService rateLimitService, CsrfTokenController csrfTokenController) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
        this.csrfTokenController = csrfTokenController;
    }

    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody UserLoginRequest request, HttpServletRequest servletRequest) {
        rateLimitService.checkLogin(servletRequest);
        rateLimitService.checkLoginByAccount(request.getUsername());
        verifyCsrf(servletRequest);
        return Result.success(authService.login(request));
    }

    @PostMapping("/register")
    public Result<AuthVO> register(@Valid @RequestBody UserRegisterRequest request, HttpServletRequest servletRequest) {
        rateLimitService.checkRegister(servletRequest);
        verifyCsrf(servletRequest);
        return Result.success(authService.register(request));
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest servletRequest) {
        if (!contactResetEnabled) {
            throw new com.ugccommunity.backend.common.exception.BusinessException(
                    com.ugccommunity.backend.common.result.ResultCode.NO_PERMISSION, "Password reset is disabled on this server");
        }
        rateLimitService.checkLogin(servletRequest);
        rateLimitService.checkLoginByAccount(request.getUsername());
        verifyCsrf(servletRequest);
        authService.resetPassword(request);
        return Result.success();
    }

    private void verifyCsrf(HttpServletRequest request) {
        String appKey = request.getHeader("X-App-Key");
        if (!StringUtils.hasText(appKey)) return;
        if (!csrfTokenController.isRequired(appKey)) return;
        String token = request.getHeader("X-CSRF-Token");
        if (!csrfTokenController.verify(appKey, token)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "CSRF token 无效或已过期，请重新获取");
        }
    }
}
