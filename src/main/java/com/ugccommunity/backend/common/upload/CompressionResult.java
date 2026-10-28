package com.ugccommunity.backend.common.upload;

public class CompressionResult {
    private String originalPath;
    private String finalPath;
    private String thumbnailPath;
    private long originalSize;
    private long finalSize;
    private boolean compressed;
    private String mediaType;
    private String message;

    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }
    public String getFinalPath() { return finalPath; }
    public void setFinalPath(String finalPath) { this.finalPath = finalPath; }
    public String getThumbnailPath() { return thumbnailPath; }
    public void setThumbnailPath(String thumbnailPath) { this.thumbnailPath = thumbnailPath; }
    public long getOriginalSize() { return originalSize; }
    public void setOriginalSize(long originalSize) { this.originalSize = originalSize; }
    public long getFinalSize() { return finalSize; }
    public void setFinalSize(long finalSize) { this.finalSize = finalSize; }
    public long getSavedBytes() { return Math.max(0L, originalSize - finalSize); }
    public double getSavedRatio() { return originalSize <= 0 ? 0.0 : getSavedBytes() * 1.0 / originalSize; }
    public double getSizeRatio() { return originalSize <= 0 ? 1.0 : Math.min(1.0, finalSize * 1.0 / originalSize); }
    public boolean isCompressed() { return compressed; }
    public void setCompressed(boolean compressed) { this.compressed = compressed; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
