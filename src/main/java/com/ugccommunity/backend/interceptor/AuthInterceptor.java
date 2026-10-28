package com.ugccommunity.backend.interceptor;

import com.ugccommunity.backend.common.utils.TokenUtils;
import com.ugccommunity.backend.entity.User;
import com.ugccommunity.backend.mapper.UserMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenUtils tokenUtils;
    private final UserMapper userMapper;

    public AuthInterceptor(TokenUtils tokenUtils, UserMapper userMapper) {
        this.tokenUtils = tokenUtils;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Long userId = tokenUtils.parseUserId(request.getHeader("Authorization"));
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() == 1 || user.getStatus() == null || user.getStatus() != 1) {
            throw new com.ugccommunity.backend.common.exception.BusinessException(com.ugccommunity.backend.common.result.ResultCode.FORBIDDEN, "账号已被封禁，禁止访问");
        }
        request.setAttribute("userId", userId);
        return true;
    }
}
