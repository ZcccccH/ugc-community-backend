package com.ugccommunity.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.entity.AppRelease;
import com.ugccommunity.backend.mapper.AppReleaseMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side CSRF token API.
 * Clients call this before login to obtain a token and send it in the X-CSRF-Token header when logging in.
 * The server looks up the CSRF secret of the matching release by AppKey to issue and verify tokens.
 */
@RestController
@RequestMapping("/api/csrf-token")
public class CsrfTokenController {
    private final AppReleaseMapper appReleaseMapper;

    public CsrfTokenController(AppReleaseMapper appReleaseMapper) {
        this.appReleaseMapper = appReleaseMapper;
    }

    @GetMapping
    public Result<Map<String, Object>> getToken(@RequestParam String appKey) {
        AppRelease release = findCsrfRelease(appKey);
        Map<String, Object> data = new HashMap<String, Object>();
        if (release == null || release.getCsrfEnabled() == null || release.getCsrfEnabled() != 1
                || !StringUtils.hasText(release.getCsrfSecret())) {
            data.put("csrfRequired", false);
            return Result.success(data);
        }
        int ttl = release.getCsrfTokenTtlSeconds() != null && release.getCsrfTokenTtlSeconds() > 0
                ? release.getCsrfTokenTtlSeconds() : 1800;
        long expiresAt = System.currentTimeMillis() + ttl * 1000L;
        String payload = appKey + ":" + expiresAt;
        String token = payload + "." + hmac(release.getCsrfSecret(), payload);
        data.put("csrfRequired", true);
        data.put("token", token);
        data.put("ttlSeconds", ttl);
        return Result.success(data);
    }

    /** Verify a CSRF token (called by AuthService during login) */
    public boolean verify(String appKey, String token) {
        if (!StringUtils.hasText(token)) return false;
        AppRelease release = findCsrfRelease(appKey);
        if (release == null || release.getCsrfEnabled() == null || release.getCsrfEnabled() != 1
                || !StringUtils.hasText(release.getCsrfSecret())) {
            return true; // not enabled: allow
        }
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) return false;
        String payload = parts[0];
        String expectedMac = hmac(release.getCsrfSecret(), payload);
        if (!java.security.MessageDigest.isEqual(
                expectedMac.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                parts[1].getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return false;
        }
        // Check expiry
        String[] fields = payload.split(":", 2);
        if (fields.length != 2) return false;
        try {
            long expiresAt = Long.parseLong(fields[1]);
            return System.currentTimeMillis() <= expiresAt;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Whether this app requires CSRF */
    public boolean isRequired(String appKey) {
        AppRelease release = findCsrfRelease(appKey);
        return release != null && release.getCsrfEnabled() != null && release.getCsrfEnabled() == 1
                && StringUtils.hasText(release.getCsrfSecret());
    }

    private AppRelease findCsrfRelease(String appKey) {
        if (!StringUtils.hasText(appKey)) return null;
        return appReleaseMapper.selectOne(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getAppKey, appKey.trim())
                .eq(AppRelease::getEnabled, 1)
                .eq(AppRelease::getCsrfEnabled, 1)
                .orderByDesc(AppRelease::getVersionCode)
                .last("limit 1"));
    }

    private String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }
}
