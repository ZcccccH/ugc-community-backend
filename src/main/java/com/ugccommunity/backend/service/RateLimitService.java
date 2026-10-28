package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {
    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<String, Deque<Long>>();

    private final SettingService settingService;

    public RateLimitService(SettingService settingService) {
        this.settingService = settingService;
    }

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.login-per-minute:8}")
    private int loginPerMinute;

    @Value("${app.security.rate-limit.register-per-hour:5}")
    private int registerPerHour;

    @Value("${app.security.rate-limit.upload-per-minute:10}")
    private int uploadPerMinute;

    @Value("${app.security.rate-limit.publish-per-minute:6}")
    private int publishPerMinute;

    @Value("${app.security.rate-limit.comment-per-minute:20}")
    private int commentPerMinute;

    @Value("${app.security.rate-limit.action-per-minute:60}")
    private int actionPerMinute;

    /** Whether to trust X-Forwarded-For / X-Real-IP from a reverse proxy. Defaults to false so attackers cannot spoof headers to bypass rate limits; set true only behind a trusted proxy. */
    @Value("${app.security.trust-proxy:false}")
    private boolean trustProxy;

    public void checkLogin(HttpServletRequest request) {
        check("login:ip:" + clientIp(request), settingService.getInt(SettingService.RATE_LIMIT_LOGIN_PER_MINUTE, loginPerMinute), 60_000L);
    }

    /** Rate limit per login account, covering cases where IP-based limits are shared (proxy/NAT) or spoofed. */
    public void checkLoginByAccount(String account) {
        if (account == null || account.trim().isEmpty()) {
            return;
        }
        check("login:acct:" + account.trim().toLowerCase(), settingService.getInt(SettingService.RATE_LIMIT_LOGIN_PER_MINUTE, loginPerMinute), 60_000L);
    }

    public void checkRegister(HttpServletRequest request) {
        check("register:ip:" + clientIp(request), settingService.getInt(SettingService.RATE_LIMIT_REGISTER_PER_HOUR, registerPerHour), 3_600_000L);
    }

    public void checkUpload(Long userId, HttpServletRequest request) {
        int limit = settingService.getInt(SettingService.RATE_LIMIT_UPLOAD_PER_MINUTE, uploadPerMinute);
        check("upload:user:" + userId, limit, 60_000L);
        check("upload:ip:" + clientIp(request), limit * 2, 60_000L);
    }

    public void checkPublish(Long userId) {
        check("publish:user:" + userId, settingService.getInt(SettingService.RATE_LIMIT_PUBLISH_PER_MINUTE, publishPerMinute), 60_000L);
    }

    public void checkComment(Long userId) {
        check("comment:user:" + userId, settingService.getInt(SettingService.RATE_LIMIT_COMMENT_PER_MINUTE, commentPerMinute), 60_000L);
    }

    public void checkAction(Long userId, String action) {
        check(action + ":user:" + userId, settingService.getInt(SettingService.RATE_LIMIT_ACTION_PER_MINUTE, actionPerMinute), 60_000L);
    }

    private void check(String key, int maxRequests, long windowMillis) {
        if (!settingService.getBoolean(SettingService.RATE_LIMIT_ENABLED, enabled) || maxRequests <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Long> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<Long>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() >= windowMillis) {
                bucket.removeFirst();
            }
            if (bucket.size() >= maxRequests) {
                throw new BusinessException(429, "操作过于频繁，请稍后再试");
            }
            bucket.addLast(now);
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && forwarded.trim().length() > 0) {
                return forwarded.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && realIp.trim().length() > 0) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
