package com.ugccommunity.backend.vo;

public class TagVO {
    private Long id;
    private String name;
    private Integer usageCount;
    private Integer disabled;
    private Boolean favorited;
    private Boolean pinned;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getUsageCount() { return usageCount; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }
    public Integer getDisabled() { return disabled; }
    public void setDisabled(Integer disabled) { this.disabled = disabled; }
    public Boolean getFavorited() { return favorited; }
    public void setFavorited(Boolean favorited) { this.favorited = favorited; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
}
