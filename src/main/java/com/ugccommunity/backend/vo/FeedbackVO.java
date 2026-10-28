package com.ugccommunity.backend.vo;

public class FeedbackVO {
    private Long id;
    private String content;
    private String status;
    private String reply;
    private String createdAt;
    private String handledAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getHandledAt() { return handledAt; }
    public void setHandledAt(String handledAt) { this.handledAt = handledAt; }
}
