package com.ugccommunity.backend.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.upload.compression")
public class UploadCompressionProperties {
    private boolean enabled = true;
    private boolean imageEnabled = true;
    private boolean videoEnabled = false;
    private int imageMaxLongEdge = 2560;
    private float imageQuality = 0.82f;
    private int thumbnailLongEdge = 480;
    private int longImageWidth = 1440;
    private int skipImageSizeKb = 500;
    private int videoMaxLongEdge = 1280;
    private int videoCrf = 28;
    private int videoWorkerCount = 1;
    private int keepOriginalBackupDays = 7;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isImageEnabled() { return imageEnabled; }
    public void setImageEnabled(boolean imageEnabled) { this.imageEnabled = imageEnabled; }
    public boolean isVideoEnabled() { return videoEnabled; }
    public void setVideoEnabled(boolean videoEnabled) { this.videoEnabled = videoEnabled; }
    public int getImageMaxLongEdge() { return imageMaxLongEdge; }
    public void setImageMaxLongEdge(int imageMaxLongEdge) { this.imageMaxLongEdge = imageMaxLongEdge; }
    public float getImageQuality() { return imageQuality; }
    public void setImageQuality(float imageQuality) { this.imageQuality = imageQuality; }
    public int getThumbnailLongEdge() { return thumbnailLongEdge; }
    public void setThumbnailLongEdge(int thumbnailLongEdge) { this.thumbnailLongEdge = thumbnailLongEdge; }
    public int getLongImageWidth() { return longImageWidth; }
    public void setLongImageWidth(int longImageWidth) { this.longImageWidth = longImageWidth; }
    public int getSkipImageSizeKb() { return skipImageSizeKb; }
    public void setSkipImageSizeKb(int skipImageSizeKb) { this.skipImageSizeKb = skipImageSizeKb; }
    public int getVideoMaxLongEdge() { return videoMaxLongEdge; }
    public void setVideoMaxLongEdge(int videoMaxLongEdge) { this.videoMaxLongEdge = videoMaxLongEdge; }
    public int getVideoCrf() { return videoCrf; }
    public void setVideoCrf(int videoCrf) { this.videoCrf = videoCrf; }
    public int getVideoWorkerCount() { return videoWorkerCount; }
    public void setVideoWorkerCount(int videoWorkerCount) { this.videoWorkerCount = videoWorkerCount; }
    public int getKeepOriginalBackupDays() { return keepOriginalBackupDays; }
    public void setKeepOriginalBackupDays(int keepOriginalBackupDays) { this.keepOriginalBackupDays = keepOriginalBackupDays; }
}
