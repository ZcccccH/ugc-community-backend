package com.ugccommunity.backend.common.result;

public enum ResultCode {
    SUCCESS(200, "操作成功"),
    FAIL(400, "操作失败"),
    UNAUTHORIZED(401, "未登录或token已过期"),
    FORBIDDEN(403, "没有权限"),
    // Business-level "not allowed": clients treat 401/403 as an expired login and sign out, so ownership checks must not use 401/403
    NO_PERMISSION(5004, "没有权限"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_ERROR(4001, "参数错误"),
    DATA_EXIST(5002, "数据已存在"),
    DATA_NOT_EXIST(5003, "数据不存在"),
    BUSINESS_ERROR(5001, "业务处理失败"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
