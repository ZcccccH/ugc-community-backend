package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_cat_state")
public class UserCatState {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private String catId;
    private Double satiety;
    private String colorCrystalId;
    private Integer feedCount;
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCatId() { return catId; }
    public void setCatId(String catId) { this.catId = catId; }
    public Double getSatiety() { return satiety; }
    public void setSatiety(Double satiety) { this.satiety = satiety; }
    public String getColorCrystalId() { return colorCrystalId; }
    public void setColorCrystalId(String colorCrystalId) { this.colorCrystalId = colorCrystalId; }
    public Integer getFeedCount() { return feedCount; }
    public void setFeedCount(Integer feedCount) { this.feedCount = feedCount; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
