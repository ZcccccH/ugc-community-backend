package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.config.QiniuProperties;
import com.ugccommunity.backend.common.config.UploadCompressionProperties;
import com.ugccommunity.backend.entity.SystemSetting;
import com.ugccommunity.backend.mapper.SystemSettingMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Runtime configuration service. Settings are stored as key-value pairs in the system_setting table;
 * database values override the defaults from application.yml and take effect immediately after saving (no restart).
 */
@Service
public class SettingService {
    // Qiniu storage setting keys
    public static final String QINIU_ENABLED = "qiniu.enabled";
    public static final String QINIU_ACCESS_KEY = "qiniu.accessKey";
    public static final String QINIU_SECRET_KEY = "qiniu.secretKey";
    public static final String QINIU_BUCKET = "qiniu.bucket";
    public static final String QINIU_DOMAIN = "qiniu.domain";
    public static final String QINIU_REGION = "qiniu.region";
    public static final String QINIU_PREFIX = "qiniu.prefix";

    // Upload compression setting keys
    public static final String COMPRESSION_ENABLED = "compression.enabled";
    public static final String COMPRESSION_IMAGE_ENABLED = "compression.imageEnabled";
    public static final String COMPRESSION_VIDEO_ENABLED = "compression.videoEnabled";
    public static final String COMPRESSION_IMAGE_MAX_LONG_EDGE = "compression.imageMaxLongEdge";
    public static final String COMPRESSION_IMAGE_QUALITY = "compression.imageQuality";
    public static final String COMPRESSION_SKIP_IMAGE_SIZE_KB = "compression.skipImageSizeKb";
    public static final String COMPRESSION_THUMBNAIL_LONG_EDGE = "compression.thumbnailLongEdge";
    public static final String COMPRESSION_LONG_IMAGE_WIDTH = "compression.longImageWidth";
    public static final String COMPRESSION_VIDEO_MAX_LONG_EDGE = "compression.videoMaxLongEdge";
    public static final String COMPRESSION_VIDEO_CRF = "compression.videoCrf";
    public static final String COMPRESSION_VIDEO_WORKER_COUNT = "compression.videoWorkerCount";
    public static final String COMPRESSION_KEEP_ORIGINAL_BACKUP_DAYS = "compression.keepOriginalBackupDays";
    public static final String COMPRESSION_FFMPEG_PATH = "compression.ffmpegPath";

    // Rate limit setting keys
    public static final String RATE_LIMIT_ENABLED = "rateLimit.enabled";
    public static final String RATE_LIMIT_LOGIN_PER_MINUTE = "rateLimit.loginPerMinute";
    public static final String RATE_LIMIT_REGISTER_PER_HOUR = "rateLimit.registerPerHour";
    public static final String RATE_LIMIT_UPLOAD_PER_MINUTE = "rateLimit.uploadPerMinute";
    public static final String RATE_LIMIT_PUBLISH_PER_MINUTE = "rateLimit.publishPerMinute";
    public static final String RATE_LIMIT_COMMENT_PER_MINUTE = "rateLimit.commentPerMinute";
    public static final String RATE_LIMIT_ACTION_PER_MINUTE = "rateLimit.actionPerMinute";

    // API signature setting keys
    public static final String SIGNATURE_ENABLED = "signature.enabled";
    public static final String SIGNATURE_PUBLIC_KEY = "signature.publicKey";
    public static final String SIGNATURE_MAX_SKEW_SECONDS = "signature.maxSkewSeconds";
    public static final String SIGNATURE_NONCE_TTL_SECONDS = "signature.nonceTtlSeconds";

    // JWT setting keys (the secret is never exposed or editable at runtime)
    public static final String JWT_EXPIRATION_SECONDS = "jwt.expirationSeconds";

    // Legacy admin-console CSRF setting (unused by the REST API)
    public static final String ADMIN_CSRF_ENABLED = "admin.csrfEnabled";
    // Content API: only used to decide whether default negative tags should be blocked.
    // An API counts as configured once its URL is set; a key or model name is not required.
    public static final String CONTENT_API_URL = "content.apiUrl";
    public static final String NEGATIVE_TAGS = "content.negativeTags";

    private final SystemSettingMapper settingMapper;
    private final QiniuProperties qiniuProperties;
    private final UploadCompressionProperties compressionProperties;

    private final Map<String, String> cache = new ConcurrentHashMap<String, String>();

    // The @Value fields below read defaults from application.yml and are used when the database has no override (kept consistent with consumers)
    @Value("${app.security.rate-limit.enabled:true}")
    private boolean rlEnabledDefault;
    @Value("${app.security.rate-limit.login-per-minute:8}")
    private int rlLoginDefault;
    @Value("${app.security.rate-limit.register-per-hour:5}")
    private int rlRegisterDefault;
    @Value("${app.security.rate-limit.upload-per-minute:10}")
    private int rlUploadDefault;
    @Value("${app.security.rate-limit.publish-per-minute:6}")
    private int rlPublishDefault;
    @Value("${app.security.rate-limit.comment-per-minute:20}")
    private int rlCommentDefault;
    @Value("${app.security.rate-limit.action-per-minute:60}")
    private int rlActionDefault;

    @Value("${app.security.signature.enabled:false}")
    private boolean sigEnabledDefault;
    @Value("${app.security.signature.public-key:}")
    private String sigPublicKeyDefault;
    @Value("${app.security.signature.max-skew-seconds:300}")
    private long sigMaxSkewDefault;
    @Value("${app.security.signature.nonce-ttl-seconds:600}")
    private long sigNonceTtlDefault;

    @Value("${app.jwt.expiration-seconds:604800}")
    private long jwtExpirationDefault;

    public SettingService(SystemSettingMapper settingMapper, QiniuProperties qiniuProperties, UploadCompressionProperties compressionProperties) {
        this.settingMapper = settingMapper;
        this.qiniuProperties = qiniuProperties;
        this.compressionProperties = compressionProperties;
    }

    @PostConstruct
    public void loadCache() {
        try {
            cache.clear();
            for (SystemSetting setting : settingMapper.selectList(null)) {
                if (setting.getConfigKey() != null && setting.getConfigValue() != null) {
                    cache.put(setting.getConfigKey(), setting.getConfigValue());
                }
            }
        } catch (Exception ignored) {
            // Ignore if the table does not exist yet; the first save after it is created writes the value
        }
    }

    public String getString(String key, String defaultValue) {
        String value = cache.get(key);
        return value == null ? defaultValue : value;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = cache.get(key);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value);
    }

    public int getInt(String key, int defaultValue) {
        String value = cache.get(key);
        if (!StringUtils.hasText(value)) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public float getFloat(String key, float defaultValue) {
        String value = cache.get(key);
        if (!StringUtils.hasText(value)) return defaultValue;
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String value = cache.get(key);
        if (!StringUtils.hasText(value)) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean negativeTagFilterActive() {
        boolean apiConfigured = StringUtils.hasText(getString(CONTENT_API_URL, ""));
        return !apiConfigured;
    }

    public Set<String> negativeTagNames() {
        Set<String> names = new LinkedHashSet<String>();
        String configured = getString(NEGATIVE_TAGS, "R18,车,血腥,po文,恐怖");
        for (String raw : configured.split("[,，]")) {
            String name = raw == null ? "" : raw.trim().replace("#", "");
            if (StringUtils.hasText(name)) names.add(name.toLowerCase());
        }
        return names;
    }

    public boolean isNegativeTag(String rawName) {
        if (!negativeTagFilterActive() || !StringUtils.hasText(rawName)) return false;
        return negativeTagNames().contains(rawName.trim().replace("#", "").toLowerCase());
    }

    public synchronized void save(String key, String value) {
        SystemSetting existing = settingMapper.selectById(key);
        String stored = value == null ? "" : value;
        if (existing == null) {
            SystemSetting setting = new SystemSetting();
            setting.setConfigKey(key);
            setting.setConfigValue(stored);
            setting.setUpdatedAt(LocalDateTime.now());
            settingMapper.insert(setting);
        } else {
            existing.setConfigValue(stored);
            existing.setUpdatedAt(LocalDateTime.now());
            settingMapper.updateById(existing);
        }
        cache.put(key, stored);
    }

    public void saveAll(Map<String, String> values) {
        if (values == null) return;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
    }

    /** Effective Qiniu configuration (database overrides yml defaults), consumed by services. */
    public Map<String, Object> effectiveQiniu() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("enabled", getBoolean(QINIU_ENABLED, qiniuProperties.isEnabled()));
        data.put("accessKey", getString(QINIU_ACCESS_KEY, qiniuProperties.getAccessKey()));
        data.put("secretKey", getString(QINIU_SECRET_KEY, qiniuProperties.getSecretKey()));
        data.put("bucket", getString(QINIU_BUCKET, qiniuProperties.getBucket()));
        data.put("domain", getString(QINIU_DOMAIN, qiniuProperties.getDomain()));
        data.put("region", getString(QINIU_REGION, qiniuProperties.getRegion()));
        data.put("prefix", getString(QINIU_PREFIX, qiniuProperties.getPrefix()));
        return data;
    }

    /** Effective upload compression configuration. */
    public Map<String, Object> effectiveCompression() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("enabled", getBoolean(COMPRESSION_ENABLED, compressionProperties.isEnabled()));
        data.put("imageEnabled", getBoolean(COMPRESSION_IMAGE_ENABLED, compressionProperties.isImageEnabled()));
        data.put("videoEnabled", getBoolean(COMPRESSION_VIDEO_ENABLED, compressionProperties.isVideoEnabled()));
        data.put("imageMaxLongEdge", getInt(COMPRESSION_IMAGE_MAX_LONG_EDGE, compressionProperties.getImageMaxLongEdge()));
        data.put("imageQuality", getFloat(COMPRESSION_IMAGE_QUALITY, compressionProperties.getImageQuality()));
        data.put("skipImageSizeKb", getInt(COMPRESSION_SKIP_IMAGE_SIZE_KB, compressionProperties.getSkipImageSizeKb()));
        data.put("thumbnailLongEdge", getInt(COMPRESSION_THUMBNAIL_LONG_EDGE, compressionProperties.getThumbnailLongEdge()));
        data.put("longImageWidth", getInt(COMPRESSION_LONG_IMAGE_WIDTH, compressionProperties.getLongImageWidth()));
        data.put("videoMaxLongEdge", getInt(COMPRESSION_VIDEO_MAX_LONG_EDGE, compressionProperties.getVideoMaxLongEdge()));
        data.put("videoCrf", getInt(COMPRESSION_VIDEO_CRF, compressionProperties.getVideoCrf()));
        data.put("videoWorkerCount", getInt(COMPRESSION_VIDEO_WORKER_COUNT, compressionProperties.getVideoWorkerCount()));
        data.put("keepOriginalBackupDays", getInt(COMPRESSION_KEEP_ORIGINAL_BACKUP_DAYS, compressionProperties.getKeepOriginalBackupDays()));
        data.put("ffmpegPath", getString(COMPRESSION_FFMPEG_PATH, "ffmpeg"));
        return data;
    }

    /** Effective rate limit configuration. */
    public Map<String, Object> effectiveRateLimit() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("enabled", getBoolean(RATE_LIMIT_ENABLED, rlEnabledDefault));
        data.put("loginPerMinute", getInt(RATE_LIMIT_LOGIN_PER_MINUTE, rlLoginDefault));
        data.put("registerPerHour", getInt(RATE_LIMIT_REGISTER_PER_HOUR, rlRegisterDefault));
        data.put("uploadPerMinute", getInt(RATE_LIMIT_UPLOAD_PER_MINUTE, rlUploadDefault));
        data.put("publishPerMinute", getInt(RATE_LIMIT_PUBLISH_PER_MINUTE, rlPublishDefault));
        data.put("commentPerMinute", getInt(RATE_LIMIT_COMMENT_PER_MINUTE, rlCommentDefault));
        data.put("actionPerMinute", getInt(RATE_LIMIT_ACTION_PER_MINUTE, rlActionDefault));
        return data;
    }

    /** Effective API signature configuration. */
    public Map<String, Object> effectiveSignature() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("enabled", getBoolean(SIGNATURE_ENABLED, sigEnabledDefault));
        data.put("publicKey", getString(SIGNATURE_PUBLIC_KEY, sigPublicKeyDefault));
        data.put("maxSkewSeconds", getLong(SIGNATURE_MAX_SKEW_SECONDS, sigMaxSkewDefault));
        data.put("nonceTtlSeconds", getLong(SIGNATURE_NONCE_TTL_SECONDS, sigNonceTtlDefault));
        return data;
    }

    /** Effective JWT configuration (the secret is never exposed). */
    public Map<String, Object> effectiveJwt() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("expirationSeconds", getLong(JWT_EXPIRATION_SECONDS, jwtExpirationDefault));
        return data;
    }
}
