package com.ugccommunity.backend.vo;

public class FavoriteCollectionVO {
    private Long id;
    private String name;
    private Integer workCount;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getWorkCount() { return workCount; }
    public void setWorkCount(Integer workCount) { this.workCount = workCount; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
