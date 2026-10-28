package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** API 请求审计日志（遵循 OWASP Logging Cheat Sheet: When/Where/Who/What） */
@TableName("api_request_log")
public class ApiRequestLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    // When
    private LocalDateTime requestTime;
    private Integer duration; // ms
    // Where
    private String method;
    private String path;
    private String queryString;
    private String clientIp;
    private String xForwardedFor;
    private String region; // IP 归属地
    // Who
    private Long userId;
    private String appKey;
    private String userAgent;
    private String deviceType; // PC/Mobile/Tablet/Bot
    private String osName;
    private String browserName;
    // What
    private Integer httpStatus;
    private String status; // success / fail / error
    private String requestHeaders; // JSON subset
    private String requestBody; // 前2KB，脱敏
    private String threatTag; // normal/auth_fail/sign_fail/rate_limit/suspicious
    private String requestId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getRequestTime() { return requestTime; }
    public void setRequestTime(LocalDateTime requestTime) { this.requestTime = requestTime; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getQueryString() { return queryString; }
    public void setQueryString(String queryString) { this.queryString = queryString; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public String getXForwardedFor() { return xForwardedFor; }
    public void setXForwardedFor(String xForwardedFor) { this.xForwardedFor = xForwardedFor; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }
    public String getBrowserName() { return browserName; }
    public void setBrowserName(String browserName) { this.browserName = browserName; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(String requestHeaders) { this.requestHeaders = requestHeaders; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getThreatTag() { return threatTag; }
    public void setThreatTag(String threatTag) { this.threatTag = threatTag; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
