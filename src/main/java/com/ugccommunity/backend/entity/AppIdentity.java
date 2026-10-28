package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 官方接口配置：应用身份与签名密钥。 */
@TableName("app_identity")
public class AppIdentity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appName;
    private String appKey;
    private String appSecret;
    private String rsaPublicKey;
    private String rsaPrivateKey;
    private Integer status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getRsaPublicKey() { return rsaPublicKey; }
    public void setRsaPublicKey(String rsaPublicKey) { this.rsaPublicKey = rsaPublicKey; }
    public String getRsaPrivateKey() { return rsaPrivateKey; }
    public void setRsaPrivateKey(String rsaPrivateKey) { this.rsaPrivateKey = rsaPrivateKey; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
