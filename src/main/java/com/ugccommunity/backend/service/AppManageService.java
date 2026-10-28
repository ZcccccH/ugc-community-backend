package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.entity.AppIdentity;
import com.ugccommunity.backend.entity.AppRelease;
import com.ugccommunity.backend.mapper.AppIdentityMapper;
import com.ugccommunity.backend.mapper.AppReleaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** App and release management: official API configuration (app identity / keys) + releases. */
@Service
public class AppManageService {
    private static final List<String> OS_OPTIONS = Arrays.asList("ios", "android", "harmony");
    private final SecureRandom random = new SecureRandom();

    private final AppIdentityMapper appIdentityMapper;
    private final AppReleaseMapper appReleaseMapper;

    public AppManageService(AppIdentityMapper appIdentityMapper, AppReleaseMapper appReleaseMapper) {
        this.appIdentityMapper = appIdentityMapper;
        this.appReleaseMapper = appReleaseMapper;
    }

    // ============================ Official API configuration (app identity) ============================

    public List<AppIdentity> apps() {
        return appIdentityMapper.selectList(new LambdaQueryWrapper<AppIdentity>().orderByDesc(AppIdentity::getId));
    }

    public List<AppIdentity> activeApps() {
        return appIdentityMapper.selectList(new LambdaQueryWrapper<AppIdentity>().eq(AppIdentity::getStatus, 1).orderByDesc(AppIdentity::getId));
    }

    /** Create an app and generate AppKey / AppSecret / RSA key pair. The returned object contains plaintext keys and should be shown only once. */
    @Transactional(rollbackFor = Exception.class)
    public AppIdentity createApp(String appName) {
        String name = trim(appName);
        require(name, "应用名称不能为空");
        if (name.length() > 100) throw new IllegalArgumentException("应用名称过长");
        AppIdentity app = new AppIdentity();
        app.setAppName(name);
        app.setAppKey(generateAppKey());
        app.setAppSecret(generateAppSecret());
        KeyPair keyPair = generateRsaKeyPair();
        app.setRsaPublicKey(pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        app.setRsaPrivateKey(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        app.setStatus(1);
        appIdentityMapper.insert(app);
        return app;
    }

    public AppIdentity appById(Long id) {
        return id == null ? null : appIdentityMapper.selectById(id);
    }

    /** Generate a fresh key set (not persisted) for previewing when creating an app; it is persisted on save. */
    public AppIdentity generateIdentity() {
        AppIdentity app = new AppIdentity();
        app.setAppKey(generateAppKey());
        app.setAppSecret(generateAppSecret());
        KeyPair keyPair = generateRsaKeyPair();
        app.setRsaPublicKey(pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        app.setRsaPrivateKey(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        app.setStatus(1);
        return app;
    }

    /**
     * Save (create a release):
     * when newApp=true, persist a new app with the previewed keys; otherwise use the selected appKey.
     * Returns the newly created app (for one-time key display), or null when an existing app was selected.
     */
    @Transactional(rollbackFor = Exception.class)
    public AppIdentity saveAppConfig(boolean newApp, String appName, String appKey, String appSecret,
                                     String rsaPublicKey, String rsaPrivateKey, String os, String variant,
                                     String versionDisplay, Integer versionCode, String fileSize, String downloadUrl,
                                     String changelog, boolean forceUpdate, boolean enabled, boolean signEnabled,
                                     Integer signMaxSkewSeconds, Integer signNonceTtlSeconds,
                                     boolean csrfEnabled, String csrfSecret, Integer csrfTokenTtlSeconds) {
        String effectiveKey;
        AppIdentity created = null;
        if (newApp) {
            require(appName, "应用名称不能为空");
            require(appKey, "缺少生成的 AppKey，请重新点击“新建应用”");
            require(appSecret, "缺少生成的 AppSecret");
            require(rsaPublicKey, "缺少 RSA 公钥");
            require(rsaPrivateKey, "缺少 RSA 私钥");
            if (appIdentityMapper.selectCount(new LambdaQueryWrapper<AppIdentity>().eq(AppIdentity::getAppKey, appKey.trim())) > 0) {
                throw new IllegalArgumentException("AppKey 已存在，请重新生成");
            }
            AppIdentity app = new AppIdentity();
            app.setAppName(appName.trim());
            app.setAppKey(appKey.trim());
            app.setAppSecret(appSecret.trim());
            app.setRsaPublicKey(rsaPublicKey);
            app.setRsaPrivateKey(rsaPrivateKey);
            app.setStatus(1);
            appIdentityMapper.insert(app);
            created = app;
            effectiveKey = app.getAppKey();
        } else {
            require(appKey, "请选择所属应用");
            AppIdentity exist = appIdentityMapper.selectOne(new LambdaQueryWrapper<AppIdentity>()
                    .eq(AppIdentity::getAppKey, appKey.trim()).last("limit 1"));
            if (exist == null) throw new IllegalArgumentException("所属应用不存在");
            effectiveKey = appKey.trim();
        }
        saveRelease(null, effectiveKey, os, variant, versionDisplay, versionCode, fileSize, downloadUrl, changelog, forceUpdate, enabled, signEnabled, signMaxSkewSeconds, signNonceTtlSeconds, csrfEnabled, csrfSecret, csrfTokenTtlSeconds);
        return created;
    }

    /** Release list; each row includes its app identity (name / AppKey / AppSecret / keys). */
    public List<Map<String, Object>> releaseRows() {
        List<AppRelease> list = releases();
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        Map<String, AppIdentity> cache = new HashMap<String, AppIdentity>();
        for (AppRelease rel : list) {
            AppIdentity app = cache.get(rel.getAppKey());
            if (app == null) {
                app = appIdentityMapper.selectOne(new LambdaQueryWrapper<AppIdentity>()
                        .eq(AppIdentity::getAppKey, rel.getAppKey()).last("limit 1"));
                if (app != null) cache.put(rel.getAppKey(), app);
            }
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("release", rel);
            row.put("app", app);
            rows.add(row);
        }
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleApp(Long id) {
        AppIdentity app = appIdentityMapper.selectById(id);
        if (app == null) return;
        app.setStatus(app.getStatus() != null && app.getStatus() == 1 ? 0 : 1);
        appIdentityMapper.updateById(app);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteApps(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        appIdentityMapper.deleteBatchIds(ids);
    }

    // ============================ Releases ============================

    public List<AppRelease> releases() {
        return appReleaseMapper.selectList(new LambdaQueryWrapper<AppRelease>()
                .orderByDesc(AppRelease::getCreatedAt));
    }

    public AppRelease releaseById(Long id) {
        return id == null ? null : appReleaseMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveRelease(Long id, String appKey, String os, String variant, String versionDisplay,
                            Integer versionCode, String fileSize, String downloadUrl, String changelog,
                            boolean forceUpdate, boolean enabled, boolean signEnabled,
                            Integer signMaxSkewSeconds, Integer signNonceTtlSeconds,
                            boolean csrfEnabled, String csrfSecret, Integer csrfTokenTtlSeconds) {
        require(appKey, "请选择所属应用");
        if (appIdentityMapper.selectCount(new LambdaQueryWrapper<AppIdentity>().eq(AppIdentity::getAppKey, appKey.trim())) == 0) {
            throw new IllegalArgumentException("所属应用不存在");
        }
        String osValue = trim(os).toLowerCase();
        if (!OS_OPTIONS.contains(osValue)) throw new IllegalArgumentException("系统必须为 iOS / 安卓 / 鸿蒙");
        require(versionDisplay, "版本号不能为空");
        if (versionCode == null || versionCode < 0) throw new IllegalArgumentException("VersionCode 必须为非负整数");

        AppRelease release = id == null ? new AppRelease() : appReleaseMapper.selectById(id);
        if (release == null) throw new IllegalArgumentException("版本记录不存在");
        release.setAppKey(appKey.trim());
        release.setOs(osValue);
        release.setVariant(emptyToNull(variant));
        release.setVersionDisplay(trim(versionDisplay));
        release.setVersionCode(versionCode);
        release.setFileSize(emptyToNull(fileSize));
        release.setDownloadUrl(emptyToNull(downloadUrl));
        release.setChangelog(emptyToNull(changelog));
        release.setForceUpdate(forceUpdate ? 1 : 0);
        release.setEnabled(enabled ? 1 : 0);
        release.setSignEnabled(signEnabled ? 1 : 0);
        release.setSignMaxSkewSeconds(signMaxSkewSeconds == null || signMaxSkewSeconds < 0 ? 300 : signMaxSkewSeconds);
        release.setSignNonceTtlSeconds(signNonceTtlSeconds == null || signNonceTtlSeconds < 0 ? 600 : signNonceTtlSeconds);
        release.setCsrfEnabled(csrfEnabled ? 1 : 0);
        release.setCsrfSecret(csrfSecret == null || csrfSecret.trim().isEmpty() ? null : csrfSecret.trim());
        release.setCsrfTokenTtlSeconds(csrfTokenTtlSeconds == null || csrfTokenTtlSeconds < 60 ? 1800 : csrfTokenTtlSeconds);
        if (id == null) {
            appReleaseMapper.insert(release);
        } else {
            appReleaseMapper.updateById(release);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleReleaseEnabled(Long id) {
        AppRelease release = appReleaseMapper.selectById(id);
        if (release == null) return;
        release.setEnabled(release.getEnabled() != null && release.getEnabled() == 1 ? 0 : 1);
        appReleaseMapper.updateById(release);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleReleaseForce(Long id) {
        AppRelease release = appReleaseMapper.selectById(id);
        if (release == null) return;
        release.setForceUpdate(release.getForceUpdate() != null && release.getForceUpdate() == 1 ? 0 : 1);
        appReleaseMapper.updateById(release);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteReleases(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        appReleaseMapper.deleteBatchIds(ids);
    }

    /** Client update check: returns the enabled record with the highest versionCode for the given app and OS. */
    public AppRelease latestRelease(String appKey, String os) {
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(os)) return null;
        return appReleaseMapper.selectOne(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getAppKey, appKey.trim())
                .eq(AppRelease::getOs, os.trim().toLowerCase())
                .eq(AppRelease::getEnabled, 1)
                .orderByDesc(AppRelease::getVersionCode)
                .last("limit 1"));
    }

    public List<String> osOptions() {
        return OS_OPTIONS;
    }

    /** Whether the app requires API signatures: true if any enabled release has signature verification turned on. */
    public boolean requiresSignature(String appKey) {
        if (!StringUtils.hasText(appKey)) return false;
        return appReleaseMapper.selectCount(new LambdaQueryWrapper<AppRelease>()
                .eq(AppRelease::getAppKey, appKey.trim())
                .eq(AppRelease::getEnabled, 1)
                .eq(AppRelease::getSignEnabled, 1)) > 0;
    }

    /** Generate a CSRF secret (32 bytes, hex). */
    public String generateCsrfSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ============================ Key generation and helpers ============================

    private String generateAppKey() {
        StringBuilder builder = new StringBuilder("YX");
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        builder.append(hex, 0, 16);
        return builder.toString();
    }

    private String generateAppSecret() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, random);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("生成 RSA 密钥失败", e);
        }
    }

    private String pem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----";
    }

    private void require(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
