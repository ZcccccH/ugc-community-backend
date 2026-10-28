package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

@Service
public class QiniuStorageService {
    private final SettingService settingService;

    public QiniuStorageService(SettingService settingService) {
        this.settingService = settingService;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(settingService.effectiveQiniu().get("enabled"));
    }

    public String upload(Path file, String objectName) {
        Map<String, Object> config = settingService.effectiveQiniu();
        validateConfig(config);
        String accessKey = str(config.get("accessKey"));
        String secretKey = str(config.get("secretKey"));
        String bucket = str(config.get("bucket"));
        String key = buildKey(objectName, str(config.get("prefix")));
        Auth auth = Auth.create(accessKey, secretKey);
        String uploadToken = auth.uploadToken(bucket, key);
        UploadManager uploadManager = new UploadManager(new Configuration(resolveRegion(str(config.get("region")))));
        Response response = null;
        try {
            response = uploadManager.put(file.toFile(), key, uploadToken);
            if (!response.isOK()) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "七牛云上传失败：" + response.statusCode);
            }
            return buildUrl(key, str(config.get("domain")));
        } catch (QiniuException e) {
            String message = e.response == null ? e.getMessage() : e.response.toString();
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "七牛云上传失败：" + message);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private static final long REACHABLE_TTL_MS = 5 * 60 * 1000L;
    private final Map<String, long[]> reachableCache = new java.util.concurrent.ConcurrentHashMap<String, long[]>();

    /**
     * After uploading, confirm the returned public URL is actually reachable. If the access domain is broken (unbound, expired, reclaimed) Qiniu still accepts the file
     * but the URL will not load; callers should then use the local copy. Results are cached per domain for a few minutes, so not every upload costs an extra request.
     */
    public boolean publicUrlReachable(String url) {
        String host;
        try {
            host = java.net.URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) return false;
        long now = System.currentTimeMillis();
        long[] cached = reachableCache.get(host);
        if (cached != null && now - cached[0] < REACHABLE_TTL_MS) return cached[1] == 1L;
        boolean ok = probe(url);
        reachableCache.put(host, new long[]{now, ok ? 1L : 0L});
        return ok;
    }

    /** Package-private so tests can replace it with an implementation that sends no requests */
    boolean probe(String url) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int status = connection.getResponseCode();
            return status >= 200 && status < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void validateConfig(Map<String, Object> config) {
        if (!StringUtils.hasText(str(config.get("accessKey")))
                || !StringUtils.hasText(str(config.get("secretKey")))
                || !StringUtils.hasText(str(config.get("bucket")))
                || !StringUtils.hasText(str(config.get("domain")))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "七牛云配置不完整");
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String buildKey(String objectName, String prefixValue) {
        String prefix = normalizePrefix(prefixValue);
        String name = objectName == null ? "" : objectName.replace("\\", "/");
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private String buildUrl(String key, String domainValue) {
        String domain = domainValue == null ? "" : domainValue.trim();
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "https://" + domain;
        }
        return domain + "/" + key;
    }

    private String normalizePrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String prefix = value.trim().replace("\\", "/");
        while (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    private Region resolveRegion(String value) {
        String region = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "auto";
        switch (region) {
            case "z0":
            case "huadong":
            case "region0":
                return Region.region0();
            case "cn-east-2":
            case "z0-2":
            case "huadong-zhejiang2":
                return Region.regionCnEast2();
            case "z1":
            case "huabei":
            case "region1":
                return Region.region1();
            case "z2":
            case "huanan":
            case "region2":
                return Region.region2();
            case "na0":
            case "beimei":
                return Region.regionNa0();
            case "as0":
            case "xinjiapo":
                return Region.regionAs0();
            case "auto":
            default:
                return Region.autoRegion();
        }
    }
}
