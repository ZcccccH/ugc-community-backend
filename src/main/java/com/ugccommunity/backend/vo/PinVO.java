package com.ugccommunity.backend.vo;

public class PinVO {
    private String targetType;
    private Long targetId;
    private Integer sortOrder;
    private String createdAt;
    private TagVO tag;
    private UserVO user;
    private WorkVO work;

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public TagVO getTag() { return tag; }
    public void setTag(TagVO tag) { this.tag = tag; }
    public UserVO getUser() { return user; }
    public void setUser(UserVO user) { this.user = user; }
    public WorkVO getWork() { return work; }
    public void setWork(WorkVO work) { this.work = work; }
}
