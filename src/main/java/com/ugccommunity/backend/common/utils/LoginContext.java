package com.ugccommunity.backend.common.utils;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class LoginContext {
    public static Long getCurrentUserIdOrThrow() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        HttpServletRequest request = attributes.getRequest();
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        }
        throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
