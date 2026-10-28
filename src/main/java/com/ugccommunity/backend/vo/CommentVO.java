package com.ugccommunity.backend.vo;

public class CommentVO {
    private Long id;
    private String content;
    private String createdAt;
    private UserVO user;
    private Boolean canDelete;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public UserVO getUser() { return user; }
    public void setUser(UserVO user) { this.user = user; }
    public Boolean getCanDelete() { return canDelete; }
    public void setCanDelete(Boolean canDelete) { this.canDelete = canDelete; }
}
