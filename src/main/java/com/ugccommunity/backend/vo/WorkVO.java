package com.ugccommunity.backend.vo;

import java.util.List;

public class WorkVO {
    private Long id;
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
    private Integer favCount;
    private Integer viewCount;
    private String createdAt;
    private UserVO author;
    private List<TagVO> tags;
    private Boolean liked;
    private Boolean favorited;
    private Boolean followedAuthor;
    private Boolean pinned;
    private Boolean resonated;
    private String resonatedCrystalId;
    private List<WorkCrystalResonanceVO> resonances;
    private Boolean allowImageSave;
    private String imageDisplayMode;
    private Boolean paidLocked;
    private String unlockCrystalId;
    private Integer unlockCrystalAmount;
    private Boolean unlockedByMe;
    private String lockPreviewText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public UserVO getAuthor() { return author; }
    public void setAuthor(UserVO author) { this.author = author; }
    public List<TagVO> getTags() { return tags; }
    public void setTags(List<TagVO> tags) { this.tags = tags; }
    public Boolean getLiked() { return liked; }
    public void setLiked(Boolean liked) { this.liked = liked; }
    public Boolean getFavorited() { return favorited; }
    public void setFavorited(Boolean favorited) { this.favorited = favorited; }
    public Boolean getFollowedAuthor() { return followedAuthor; }
    public void setFollowedAuthor(Boolean followedAuthor) { this.followedAuthor = followedAuthor; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
    public Boolean getResonated() { return resonated; }
    public void setResonated(Boolean resonated) { this.resonated = resonated; }
    public String getResonatedCrystalId() { return resonatedCrystalId; }
    public void setResonatedCrystalId(String resonatedCrystalId) { this.resonatedCrystalId = resonatedCrystalId; }
    public List<WorkCrystalResonanceVO> getResonances() { return resonances; }
    public void setResonances(List<WorkCrystalResonanceVO> resonances) { this.resonances = resonances; }
    public Boolean getAllowImageSave() { return allowImageSave; }
    public void setAllowImageSave(Boolean allowImageSave) { this.allowImageSave = allowImageSave; }
    public String getImageDisplayMode() { return imageDisplayMode; }
    public void setImageDisplayMode(String imageDisplayMode) { this.imageDisplayMode = imageDisplayMode; }
    public Boolean getPaidLocked() { return paidLocked; }
    public void setPaidLocked(Boolean paidLocked) { this.paidLocked = paidLocked; }
    public String getUnlockCrystalId() { return unlockCrystalId; }
    public void setUnlockCrystalId(String unlockCrystalId) { this.unlockCrystalId = unlockCrystalId; }
    public Integer getUnlockCrystalAmount() { return unlockCrystalAmount; }
    public void setUnlockCrystalAmount(Integer unlockCrystalAmount) { this.unlockCrystalAmount = unlockCrystalAmount; }
    public Boolean getUnlockedByMe() { return unlockedByMe; }
    public void setUnlockedByMe(Boolean unlockedByMe) { this.unlockedByMe = unlockedByMe; }
    public String getLockPreviewText() { return lockPreviewText; }
    public void setLockPreviewText(String lockPreviewText) { this.lockPreviewText = lockPreviewText; }

    /** 这篇作品所属的合辑（别人看只有公开合辑）；只在作品详情里填，列表接口为空 */
    private java.util.List<WorkCollectionBriefVO> collections;
    public java.util.List<WorkCollectionBriefVO> getCollections() { return collections; }
    public void setCollections(java.util.List<WorkCollectionBriefVO> collections) { this.collections = collections; }
}
