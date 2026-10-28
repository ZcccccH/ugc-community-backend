package com.ugccommunity.backend.common.result;

public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    public Result() {
    }

    public Result(Integer code, String message, T data, Long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data, System.currentTimeMillis());
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<T>(ResultCode.SUCCESS.getCode(), message, data, System.currentTimeMillis());
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<T>(resultCode.getCode(), resultCode.getMessage(), null, System.currentTimeMillis());
    }

    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<T>(resultCode.getCode(), message, null, System.currentTimeMillis());
    }

    public static <T> Result<T> error(Integer code, String message) {
        return new Result<T>(code, message, null, System.currentTimeMillis());
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
