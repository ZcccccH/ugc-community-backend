package com.ugccommunity.backend.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.entity.AppIdentity;
import com.ugccommunity.backend.entity.AppRelease;
import com.ugccommunity.backend.mapper.AppIdentityMapper;
import com.ugccommunity.backend.mapper.AppReleaseMapper;
import com.ugccommunity.backend.service.SettingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Official API signature filter. When enabled, for /api requests it performs:
 * 1) Request verification: the client signs the request with the app's AppSecret using HMAC-SHA256 and the server verifies it with the same AppSecret (client identity).
 * 2) Response signing: the server signs the response body with the app's RSA private key (server identity) and the client verifies it with the RSA public key.
 * The app is identified by the X-App-Key request header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiSignatureFilter extends OncePerRequestFilter {
    private static final String HEADER_APP_KEY = "X-App-Key";
    private static final String HEADER_TIMESTAMP = "X-App-Timestamp";
    private static final String HEADER_NONCE = "X-App-Nonce";
    private static final String HEADER_SIGNATURE = "X-App-Signature";
    private static final String HEADER_RESP_TIMESTAMP = "X-Resp-Timestamp";
    private static final String HEADER_RESP_SIGNATURE = "X-Resp-Signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String RSA_SIGN_ALGORITHM = "SHA256withRSA";

    private final Map<String, Long> usedNonces = new ConcurrentHashMap<String, Long>();

    @Value("${app.security.signature.enabled:false}")
    private boolean enabled;

    @Value("${app.security.signature.max-skew-seconds:300}")
    private long maxSkewSeconds;

    @Value("${app.security.signature.nonce-ttl-seconds:600}")
    private long nonceTtlSeconds;

    private final SettingService settingService;
    private final AppIdentityMapper appIdentityMapper;
    private final AppReleaseMapper appReleaseMapper;

    public ApiSignatureFilter(SettingService settingService, AppIdentityMapper appIdentityMapper, AppReleaseMapper appReleaseMapper) {
        this.settingService = settingService;
        this.appIdentityMapper = appIdentityMapper;
        this.appReleaseMapper = appReleaseMapper;
    }

    private boolean sigEnabled() {
        return settingService.getBoolean(SettingService.SIGNATURE_ENABLED, enabled);
    }

    /** Whether the app requires signatures: true if any enabled release has signature verification turned on. */
    private boolean requiresSignature(String appKey) {
        if (!StringUtils.hasText(appKey)) return false;
        return appReleaseMapper.selectCount(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getAppKey, appKey.trim())
                .eq(AppRelease::getEnabled, 1)
                .eq(AppRelease::getSignEnabled, 1)) > 0;
    }

    private long sigMaxSkew() {
        return settingService.getLong(SettingService.SIGNATURE_MAX_SKEW_SECONDS, maxSkewSeconds);
    }

    private long sigNonceTtl() {
        return settingService.getLong(SettingService.SIGNATURE_NONCE_TTL_SECONDS, nonceTtlSeconds);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String appKey = request.getHeader(HEADER_APP_KEY);
        // Only verify/sign requests from official apps that send an AppKey and have signature verification enabled
        if (!StringUtils.hasText(appKey) || !requiresSignature(appKey)) {
            filterChain.doFilter(request, response);
            return;
        }
        AppIdentity app;
        byte[] body;
        try {
            app = resolveApp(appKey);
            body = isMultipart(request) ? new byte[0] : readBody(request);
            verifyRequest(request, body, app);
        } catch (BusinessException e) {
            writeError(response, e);
            return;
        }

        HttpServletRequest effectiveRequest = isMultipart(request) ? request : new CachedBodyRequest(request, body);
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(effectiveRequest, wrapper);
        signResponse(wrapper, app);
        wrapper.copyBodyToResponse();
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || !path.startsWith("/api/") || "/api/health".equals(path);
    }

    /** Resolve the app from X-App-Key; when signatures are on, the header is required and the app must be active. */
    private AppIdentity resolveApp(String appKey) {
        if (!StringUtils.hasText(appKey)) {
            throw new BusinessException(401, "缺少应用标识(X-App-Key)");
        }
        AppIdentity app = appIdentityMapper.selectOne(new LambdaQueryWrapper<AppIdentity>()
                .eq(AppIdentity::getAppKey, appKey.trim()).last("limit 1"));
        if (app == null) {
            throw new BusinessException(401, "应用不存在");
        }
        if (app.getStatus() == null || app.getStatus() != 1) {
            throw new BusinessException(401, "应用已停用");
        }
        return app;
    }

    /** Request verification: HMAC-SHA256(AppSecret, canonical string). */
    private void verifyRequest(HttpServletRequest request, byte[] body, AppIdentity app) {
        if (!StringUtils.hasText(app.getAppSecret())) {
            throw new BusinessException(401, "应用未配置 AppSecret");
        }
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String signature = request.getHeader(HEADER_SIGNATURE);
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce) || !StringUtils.hasText(signature)) {
            throw new BusinessException(401, "缺少接口签名");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BusinessException(401, "接口签名时间无效");
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - ts) > sigMaxSkew() * 1000L) {
            throw new BusinessException(401, "接口签名已过期");
        }
        cleanupNonces(now);
        Long existing = usedNonces.putIfAbsent(app.getAppKey() + ":" + nonce, now);
        if (existing != null && now - existing < sigNonceTtl() * 1000L) {
            throw new BusinessException(401, "请勿重复提交请求");
        }
        String canonical = canonical(request, app.getAppKey(), timestamp, nonce, body);
        String expected = hmacSha256(app.getAppSecret(), canonical);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(401, "接口签名错误");
        }
    }

    /** Response signing: sign the response body with the app's RSA private key and write it to a response header for the client to verify. */
    private void signResponse(ContentCachingResponseWrapper wrapper, AppIdentity app) {
        try {
            if (!StringUtils.hasText(app.getRsaPrivateKey())) {
                return;
            }
            byte[] body = wrapper.getContentAsByteArray();
            long ts = System.currentTimeMillis();
            String payload = ts + "\n" + sha256(body);
            Signature signer = Signature.getInstance(RSA_SIGN_ALGORITHM);
            signer.initSign(privateKey(app.getRsaPrivateKey()));
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            String sig = Base64.getEncoder().encodeToString(signer.sign());
            wrapper.setHeader(HEADER_RESP_TIMESTAMP, String.valueOf(ts));
            wrapper.setHeader(HEADER_RESP_SIGNATURE, sig);
        } catch (Exception e) {
            logger.warn("response signing failed: " + e.getMessage());
        }
    }

    private String canonical(HttpServletRequest request, String appKey, String timestamp, String nonce, byte[] body) {
        String query = request.getQueryString();
        String path = request.getRequestURI() + (StringUtils.hasText(query) ? "?" + query : "");
        // Bind Content-Type (including the multipart boundary) and Content-Length
        // so the boundary and length of upload requests are also covered by the signature (multipart bodies are impractical to hash byte by byte in a filter,
        // so type + length + HTTPS serve as layered protection).
        String contentType = request.getContentType() == null ? "" : request.getContentType();
        String contentLength = request.getContentLength() < 0 ? "" : String.valueOf(request.getContentLength());
        return request.getMethod().toUpperCase() + "\n"
                + path + "\n"
                + appKey + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + contentType + "\n"
                + contentLength + "\n"
                + sha256(body);
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(401, "接口签名校验失败");
        }
    }

    private PrivateKey privateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new BusinessException(401, "接口签名摘要失败");
        }
    }

    private void writeError(HttpServletResponse response, BusinessException e) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + e.getCode() + ",\"message\":\"" + escape(e.getBusinessMessage()) + "\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}");
    }

    private byte[] readBody(HttpServletRequest request) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = request.getInputStream().read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void cleanupNonces(long now) {
        long ttl = sigNonceTtl();
        Iterator<Map.Entry<String, Long>> iterator = usedNonces.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() >= ttl * 1000L) {
                iterator.remove();
            }
        }
    }

    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return input.read();
                }
            };
        }
    }
}
