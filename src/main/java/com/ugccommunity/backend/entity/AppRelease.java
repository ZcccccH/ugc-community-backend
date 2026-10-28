package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 版本迭代：App 版本发布记录。 */
@TableName("app_release")
public class AppRelease {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appKey;
    private String os;
    private String variant;
    private String versionDisplay;
    private Integer versionCode;
    private String fileSize;
    private String downloadUrl;
    private String changelog;
    private Integer forceUpdate;
    private Integer enabled;
    private Integer signEnabled;
    private Integer signMaxSkewSeconds;
    private Integer signNonceTtlSeconds;
    private Integer csrfEnabled;
    private String csrfSecret;
    private Integer csrfTokenTtlSeconds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }
    public String getVersionDisplay() { return versionDisplay; }
    public void setVersionDisplay(String versionDisplay) { this.versionDisplay = versionDisplay; }
    public Integer getVersionCode() { return versionCode; }
    public void setVersionCode(Integer versionCode) { this.versionCode = versionCode; }
    public String getFileSize() { return fileSize; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }
    public Integer getForceUpdate() { return forceUpdate; }
    public void setForceUpdate(Integer forceUpdate) { this.forceUpdate = forceUpdate; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public Integer getSignEnabled() { return signEnabled; }
    public void setSignEnabled(Integer signEnabled) { this.signEnabled = signEnabled; }
    public Integer getSignMaxSkewSeconds() { return signMaxSkewSeconds; }
    public void setSignMaxSkewSeconds(Integer signMaxSkewSeconds) { this.signMaxSkewSeconds = signMaxSkewSeconds; }
    public Integer getSignNonceTtlSeconds() { return signNonceTtlSeconds; }
    public void setSignNonceTtlSeconds(Integer signNonceTtlSeconds) { this.signNonceTtlSeconds = signNonceTtlSeconds; }
    public Integer getCsrfEnabled() { return csrfEnabled; }
    public void setCsrfEnabled(Integer csrfEnabled) { this.csrfEnabled = csrfEnabled; }
    public String getCsrfSecret() { return csrfSecret; }
    public void setCsrfSecret(String csrfSecret) { this.csrfSecret = csrfSecret; }
    public Integer getCsrfTokenTtlSeconds() { return csrfTokenTtlSeconds; }
    public void setCsrfTokenTtlSeconds(Integer csrfTokenTtlSeconds) { this.csrfTokenTtlSeconds = csrfTokenTtlSeconds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
