package com.ugccommunity.backend.common.utils;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.service.SettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class TokenUtils {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TokenUtils.class);

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-seconds}")
    private long expirationSeconds;

    private final SettingService settingService;

    public TokenUtils(SettingService settingService) {
        this.settingService = settingService;
    }

    /**
     * Fails fast on startup when the HMAC signing secret is missing or too weak,
     * because a weak secret would let anyone forge a token for any user.
     */
    @javax.annotation.PostConstruct
    public void checkSecret() {
        if (secret == null || secret.trim().length() < 32) {
            log.error("JWT_SECRET is missing or shorter than 32 characters; refusing to start.");
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be a random value of at least 32 characters");
        }
    }

    public String generateToken(Long userId, String username) {
        long effectiveExpiration = settingService.getLong(SettingService.JWT_EXPIRATION_SECONDS, expirationSeconds);
        long expiresAt = System.currentTimeMillis() + effectiveExpiration * 1000L;
        String payload = userId + ":" + username + ":" + expiresAt;
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + sign(body);
    }

    public Long parseUserId(String authorization) {
        String token = resolveToken(authorization);
        String[] parts = token.split("\\.");
        if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] fields = payload.split(":");
        if (fields.length < 3) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        long expiresAt = Long.parseLong(fields[2]);
        if (System.currentTimeMillis() > expiresAt) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return Long.parseLong(fields[0]);
    }

    private String resolveToken(String authorization) {
        if (authorization == null || authorization.trim().length() == 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String value = authorization.trim();
        if (value.toLowerCase().startsWith("bearer ")) {
            value = value.substring(7).trim();
        }
        return value;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("token sign failed", e);
        }
    }
}
