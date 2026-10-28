package com.ugccommunity.backend.vo;

public class WorkCollectionVO {
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

    // 合辑详情、作者主页合辑列表、我收藏的合辑用到的字段
    private UserVO author;
    private Integer favCount;
    private Boolean favorited;
    private Boolean isOwner;
    private String visibility;
    private String cover;
    private String updatedAt;
    /** 我收藏的合辑：已删除或作者改成私密时为 true，仍返回以便取消收藏 */
    private Boolean invalid;

    public UserVO getAuthor() { return author; }
    public void setAuthor(UserVO author) { this.author = author; }
    public Integer getFavCount() { return favCount; }
    public void setFavCount(Integer favCount) { this.favCount = favCount; }
    public Boolean getFavorited() { return favorited; }
    public void setFavorited(Boolean favorited) { this.favorited = favorited; }
    public Boolean getIsOwner() { return isOwner; }
    public void setIsOwner(Boolean isOwner) { this.isOwner = isOwner; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public Boolean getInvalid() { return invalid; }
    public void setInvalid(Boolean invalid) { this.invalid = invalid; }
}
