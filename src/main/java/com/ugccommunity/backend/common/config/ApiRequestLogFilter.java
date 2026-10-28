package com.ugccommunity.backend.common.config;

import com.ugccommunity.backend.entity.ApiRequestLog;
import com.ugccommunity.backend.mapper.ApiRequestLogMapper;
import com.ugccommunity.backend.service.SettingService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.annotation.PreDestroy;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * API request audit log filter. Records /api/** requests only and writes them to the database asynchronously.
 * Follows the When/Where/Who/What principles of the OWASP Logging Cheat Sheet.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiRequestLogFilter extends OncePerRequestFilter {

    private final ApiRequestLogMapper logMapper;
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "api-request-log-writer");
        thread.setDaemon(true);
        return thread;
    });

    /** Whether to trust X-Forwarded-For / X-Real-IP from a reverse proxy. Defaults to false; enable only behind a trusted proxy so clients cannot spoof their source IP in the audit log. */
    @org.springframework.beans.factory.annotation.Value("${app.security.trust-proxy:false}")
    private boolean trustProxy;

    public ApiRequestLogFilter(ApiRequestLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        request.setAttribute("_requestId", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                long duration = System.currentTimeMillis() - start;
                ApiRequestLog log = buildLog(request, response, requestId, (int) duration);
                logExecutor.execute(() -> {
                    try {
                        logMapper.insert(log);
                    } catch (Exception ignored) {
                        // Logging must never break the request
                    }
                });
            } catch (Exception ignored) {
                // Logging must never break the request
            }
        }
    }

    @PreDestroy
    public void shutdownLogExecutor() {
        logExecutor.shutdown();
    }

    private ApiRequestLog buildLog(HttpServletRequest request, HttpServletResponse response, String requestId, int duration) {
        ApiRequestLog log = new ApiRequestLog();
        // When
        log.setRequestTime(LocalDateTime.now());
        log.setDuration(duration);
        log.setRequestId(requestId);
        // Where
        log.setMethod(request.getMethod());
        log.setPath(request.getRequestURI());
        log.setQueryString(truncate(request.getQueryString(), 2000));
        log.setClientIp(resolveClientIp(request));
        log.setXForwardedFor(truncate(request.getHeader("X-Forwarded-For"), 500));
        log.setRegion(resolveRegion(log.getClientIp()));
        // Who
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long) {
            log.setUserId((Long) userId);
        } else if (userId instanceof Number) {
            log.setUserId(((Number) userId).longValue());
        }
        log.setAppKey(truncate(request.getHeader("X-App-Key"), 64));
        String ua = truncate(request.getHeader("User-Agent"), 500);
        log.setUserAgent(ua);
        parseUserAgent(log, ua);
        // What
        int httpStatus = response.getStatus();
        log.setHttpStatus(httpStatus);
        log.setStatus(httpStatus >= 200 && httpStatus < 400 ? "success" : (httpStatus >= 400 && httpStatus < 500 ? "fail" : "error"));
        log.setRequestHeaders(buildHeadersJson(request));
        log.setRequestBody(sanitizeBody(request));
        log.setThreatTag(detectThreat(request, response, httpStatus));
        return log;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String resolveRegion(String ip) {
        if (!StringUtils.hasText(ip)) return "未知";
        // Simple check: private network / localhost
        if (ip.startsWith("127.") || ip.startsWith("192.168.") || ip.startsWith("10.") || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "本地/内网";
        }
        // In production you can plug in ip2region or MaxMind GeoLite2; here the location is marked as "unresolved"
        return "待解析";
    }

    private void parseUserAgent(ApiRequestLog log, String ua) {
        if (!StringUtils.hasText(ua)) {
            log.setDeviceType("Unknown");
            log.setOsName("Unknown");
            log.setBrowserName("Unknown");
            return;
        }
        String lower = ua.toLowerCase(Locale.ROOT);
        // Device type
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) {
            log.setDeviceType("Mobile");
        } else if (lower.contains("tablet") || lower.contains("ipad")) {
            log.setDeviceType("Tablet");
        } else if (lower.contains("bot") || lower.contains("spider") || lower.contains("crawler")) {
            log.setDeviceType("Bot");
        } else {
            log.setDeviceType("PC");
        }
        // OS
        if (lower.contains("windows")) log.setOsName("Windows");
        else if (lower.contains("mac os") || lower.contains("macintosh")) log.setOsName("macOS");
        else if (lower.contains("iphone") || lower.contains("ipad")) log.setOsName("iOS");
        else if (lower.contains("android")) log.setOsName("Android");
        else if (lower.contains("linux")) log.setOsName("Linux");
        else if (lower.contains("harmonyos")) log.setOsName("HarmonyOS");
        else log.setOsName("Other");
        // Browser
        if (lower.contains("edg")) log.setBrowserName("Edge");
        else if (lower.contains("chrome") && !lower.contains("edg")) log.setBrowserName("Chrome");
        else if (lower.contains("firefox")) log.setBrowserName("Firefox");
        else if (lower.contains("safari") && !lower.contains("chrome")) log.setBrowserName("Safari");
        else if (lower.contains("okhttp")) log.setBrowserName("OkHttp");
        else if (lower.contains("postman")) log.setBrowserName("Postman");
        else if (lower.contains("curl")) log.setBrowserName("cURL");
        else log.setBrowserName("Other");
    }

    private String buildHeadersJson(HttpServletRequest request) {
        String[] keys = {"User-Agent", "Referer", "Accept", "Content-Type", "X-App-Key", "X-Forwarded-For", "X-Real-IP", "Origin", "Authorization"};
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String key : keys) {
            String value = request.getHeader(key);
            if (StringUtils.hasText(value)) {
                if (!first) sb.append(",");
                // Mask the Authorization header
                if ("Authorization".equalsIgnoreCase(key) && value.length() > 20) {
                    value = value.substring(0, 10) + "***" + value.substring(value.length() - 4);
                }
                sb.append("\"").append(key).append("\":\"").append(escapeJson(truncate(value, 200))).append("\"");
                first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String sanitizeBody(HttpServletRequest request) {
        // Only record the first 2 KB of non-upload POST/PUT/PATCH request bodies
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method) && !"PATCH".equalsIgnoreCase(method)) {
            return null;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return "[multipart/form-data]";
        }
        // Note: if another filter already consumed the body it may be unavailable here; it is passed via a request attribute
        Object cached = request.getAttribute("_cachedBody");
        if (cached instanceof byte[]) {
            String body = new String((byte[]) cached, java.nio.charset.StandardCharsets.UTF_8);
            body = truncate(body, 2048);
            // Mask password/secret/token fields (incl. newPassword, oldPassword)
            body = body.replaceAll("(?i)(\"[a-z_]*(password|secret|token)\"\\s*:\\s*\")[^\"]*\"", "$1***\"");
            return body;
        }
        return null;
    }

    private String detectThreat(HttpServletRequest request, HttpServletResponse response, int httpStatus) {
        // Derive threat tags from the response status and known error patterns
        if (httpStatus == 401) return "auth_fail";
        if (httpStatus == 429) return "rate_limit";
        if (httpStatus == 403) return "forbidden";
        // Signature failures (code=401 inside the body can still be HTTP 200; this check uses the HTTP status)
        Object sigFail = request.getAttribute("_signFail");
        if (Boolean.TRUE.equals(sigFail)) return "sign_fail";
        if (httpStatus >= 500) return "server_error";
        return "normal";
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
