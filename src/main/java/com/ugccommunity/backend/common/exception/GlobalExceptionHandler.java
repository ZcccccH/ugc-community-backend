package com.ugccommunity.backend.common.exception;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.result.ResultCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern COLUMN_PATTERN = Pattern.compile("column '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> FIELD_NAMES = new HashMap<String, String>();

    static {
        FIELD_NAMES.put("username", "账号");
        FIELD_NAMES.put("password", "密码");
        FIELD_NAMES.put("nickname", "昵称");
        FIELD_NAMES.put("phone", "手机号");
        FIELD_NAMES.put("email", "邮箱");
        FIELD_NAMES.put("name", "名称");
        FIELD_NAMES.put("title", "标题");
        FIELD_NAMES.put("content", "内容");
        FIELD_NAMES.put("type", "类型");
        FIELD_NAMES.put("status", "状态");
        FIELD_NAMES.put("reason", "原因");
        FIELD_NAMES.put("audit_reason", "处理说明");
        FIELD_NAMES.put("media_url", "媒体文件");
        FIELD_NAMES.put("cover", "封面");
        FIELD_NAMES.put("user_id", "用户");
        FIELD_NAMES.put("work_id", "作品");
        FIELD_NAMES.put("tag_id", "Tag");
        FIELD_NAMES.put("collection_id", "合辑");
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getBusinessMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Result.error(ResultCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        String rawMessage = e.getMostSpecificCause() == null ? "" : e.getMostSpecificCause().getMessage();
        String message = resolveDataIntegrityMessage(rawMessage);
        ResultCode code = message.contains("已存在") || message.contains("重复") ? ResultCode.DATA_EXIST : ResultCode.PARAM_ERROR;
        return Result.error(code, message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("未处理的系统异常", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }

    private String resolveDataIntegrityMessage(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage;
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("uk_user_username")) return "账号已存在";
        if (lower.contains("uk_user_phone")) return "手机号已绑定其他账号";
        if (lower.contains("uk_user_email")) return "邮箱已绑定其他账号";
        if (lower.contains("uk_tag_name")) return "Tag名称已存在";
        if (lower.contains("uk_ban_word")) return "违禁词已存在";
        if (lower.contains("duplicate entry")) return "数据已存在，请勿重复提交";

        if (lower.contains("cannot be null") || lower.contains("doesn't have a default value")) {
            String field = displayFieldName(extractColumn(message));
            return field + "不能为空";
        }
        if (lower.contains("data too long")) {
            String field = displayFieldName(extractColumn(message));
            return field + "长度超出限制";
        }
        if (lower.contains("foreign key constraint fails")) {
            if (lower.contains("fk_work_user") || lower.contains("fk_message_user") || lower.contains("fk_report_reporter")
                    || lower.contains("fk_like_user") || lower.contains("fk_favorite_user") || lower.contains("fk_follow_user")) {
                return "用户不存在或已被删除";
            }
            if (lower.contains("fk_work_tag_tag")) return "Tag不存在或已被删除";
            if (lower.contains("fk_report_work") || lower.contains("fk_like_work") || lower.contains("fk_favorite_work")
                    || lower.contains("fk_work_tag_work")) {
                return "作品不存在或已被删除";
            }
            return "关联数据不存在，请刷新后重试";
        }
        if (lower.contains("incorrect") || lower.contains("truncated") || lower.contains("out of range")) {
            String field = displayFieldName(extractColumn(message));
            return field + "格式不正确";
        }
        return "数据保存失败，请检查必填项、长度和是否重复";
    }

    private String extractColumn(String message) {
        Matcher matcher = COLUMN_PATTERN.matcher(message == null ? "" : message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String displayFieldName(String column) {
        if (column == null || column.trim().length() == 0) return "填写信息";
        String name = FIELD_NAMES.get(column);
        return name == null ? column : name;
    }
}
