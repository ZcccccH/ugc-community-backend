package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_work_resonance")
public class UserWorkResonance {
    private Long userId;
    private Long workId;
    private String crystalId;
    private LocalDateTime createdAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getCrystalId() { return crystalId; }
    public void setCrystalId(String crystalId) { this.crystalId = crystalId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
