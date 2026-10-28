package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("user_crystal_state")
public class UserCrystalState {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private String crystalId;
    private Integer energy;
    private Integer crystalCount;
    private Integer dailyGainCount;
    private LocalDate dailyGainDate;
    private LocalDate dailyRedeemDate;
    private Integer active;
    private LocalDateTime updatedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCrystalId() { return crystalId; }
    public void setCrystalId(String crystalId) { this.crystalId = crystalId; }
    public Integer getEnergy() { return energy; }
    public void setEnergy(Integer energy) { this.energy = energy; }
    public Integer getCrystalCount() { return crystalCount; }
    public void setCrystalCount(Integer crystalCount) { this.crystalCount = crystalCount; }
    public Integer getDailyGainCount() { return dailyGainCount; }
    public void setDailyGainCount(Integer dailyGainCount) { this.dailyGainCount = dailyGainCount; }
    public LocalDate getDailyGainDate() { return dailyGainDate; }
    public void setDailyGainDate(LocalDate dailyGainDate) { this.dailyGainDate = dailyGainDate; }
    public LocalDate getDailyRedeemDate() { return dailyRedeemDate; }
    public void setDailyRedeemDate(LocalDate dailyRedeemDate) { this.dailyRedeemDate = dailyRedeemDate; }
    public Integer getActive() { return active; }
    public void setActive(Integer active) { this.active = active; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
