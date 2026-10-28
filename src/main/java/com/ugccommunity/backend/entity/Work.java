package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("work")
public class Work {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String cover;
    private String mediaUrl;
    private String duration;
    private String location;
    private String status;
    private Integer editCount;
    private String auditReason;
    private Integer likeCount;
    @TableField("fav_count")
    private Integer favCount;
    private Integer viewCount;
    private Integer isDeleted;
    private Integer allowImageSave;
    private String imageDisplayMode;
    private Integer paidLocked;
    private String unlockCrystalId;
    private Integer unlockCrystalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getEditCount() { return editCount; }
    public void setEditCount(Integer editCount) { this.editCount = editCount; }
    public String getAuditReason() { return auditReason; }
    public void setAuditReason(String auditReason) { this.auditReason = auditReason; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getFavCount() { return favCount; }
    public void setFavCount(Integer favCount) { this.favCount = favCount; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public Integer getAllowImageSave() { return allowImageSave; }
    public void setAllowImageSave(Integer allowImageSave) { this.allowImageSave = allowImageSave; }
    public String getImageDisplayMode() { return imageDisplayMode; }
    public void setImageDisplayMode(String imageDisplayMode) { this.imageDisplayMode = imageDisplayMode; }
    public Integer getPaidLocked() { return paidLocked; }
    public void setPaidLocked(Integer paidLocked) { this.paidLocked = paidLocked; }
    public String getUnlockCrystalId() { return unlockCrystalId; }
    public void setUnlockCrystalId(String unlockCrystalId) { this.unlockCrystalId = unlockCrystalId; }
    public Integer getUnlockCrystalAmount() { return unlockCrystalAmount; }
    public void setUnlockCrystalAmount(Integer unlockCrystalAmount) { this.unlockCrystalAmount = unlockCrystalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
