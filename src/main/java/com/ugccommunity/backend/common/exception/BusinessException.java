package com.ugccommunity.backend.common.exception;

import com.ugccommunity.backend.common.result.ResultCode;

public class BusinessException extends RuntimeException {
    private final Integer code;
    private final String businessMessage;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.businessMessage = resultCode.getMessage();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.businessMessage = message;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.businessMessage = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getBusinessMessage() {
        return businessMessage;
    }
}
