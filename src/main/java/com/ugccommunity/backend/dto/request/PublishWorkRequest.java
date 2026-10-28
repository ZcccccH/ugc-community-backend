package com.ugccommunity.backend.dto.request;

import javax.validation.constraints.NotBlank;
import java.util.List;

public class PublishWorkRequest {
    @NotBlank(message = "内容类型不能为空")
    private String type;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;
    private String cover;
    private String mediaUrl;
    private List<String> mediaUrls;
    private String location;
    private List<String> tags;
    private List<Long> collectionIds;
    private Boolean allowImageSave;
    private String imageDisplayMode;
    private Boolean paidLocked;
    private String unlockCrystalId;
    private Integer unlockCrystalAmount;
    private Boolean privateView;
    private Boolean isPrivate;
    private String visibility;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public List<String> getMediaUrls() {
        return mediaUrls;
    }

    public void setMediaUrls(List<String> mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<Long> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Long> collectionIds) {
        this.collectionIds = collectionIds;
    }
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
    public Boolean getPrivateView() { return privateView; }
    public void setPrivateView(Boolean privateView) { this.privateView = privateView; }
    public Boolean getIsPrivate() { return isPrivate; }
    public void setIsPrivate(Boolean isPrivate) { this.isPrivate = isPrivate; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public boolean isPrivateView() {
        if (Boolean.TRUE.equals(privateView) || Boolean.TRUE.equals(isPrivate)) return true;
        return "private".equalsIgnoreCase(visibility);
    }
}
