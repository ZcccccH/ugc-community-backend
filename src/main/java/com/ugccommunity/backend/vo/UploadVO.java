package com.ugccommunity.backend.vo;

public class UploadVO {
    private String url;
    private String originalName;
    private Long size;
    private String contentType;
    private String thumbnailUrl;
    private Long originalSize;
    private Long compressedSize;
    private Long savedBytes;
    private Double savedRatio;
    private Double sizeRatio;
    private Boolean compressed;
    private Double compressionRatio;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    public Long getOriginalSize() { return originalSize; }
    public void setOriginalSize(Long originalSize) { this.originalSize = originalSize; }
    public Long getCompressedSize() { return compressedSize; }
    public void setCompressedSize(Long compressedSize) { this.compressedSize = compressedSize; }
    public Long getSavedBytes() { return savedBytes; }
    public void setSavedBytes(Long savedBytes) { this.savedBytes = savedBytes; }
    public Double getSavedRatio() { return savedRatio; }
    public void setSavedRatio(Double savedRatio) { this.savedRatio = savedRatio; }
    public Double getSizeRatio() { return sizeRatio; }
    public void setSizeRatio(Double sizeRatio) { this.sizeRatio = sizeRatio; }
    public Boolean getCompressed() { return compressed; }
    public void setCompressed(Boolean compressed) { this.compressed = compressed; }
    public Double getCompressionRatio() { return compressionRatio; }
    public void setCompressionRatio(Double compressionRatio) { this.compressionRatio = compressionRatio; }
}
