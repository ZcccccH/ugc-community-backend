package com.ugccommunity.backend.vo;

public class CrystalStateVO {
    private String crystalId;
    private Integer energy;
    private Boolean active;
    private Integer crystalCount;
    private Integer dailyGainCount;
    private Integer dailyLimit;
    private Boolean dailyRedeemed;

    public String getCrystalId() { return crystalId; }
    public void setCrystalId(String crystalId) { this.crystalId = crystalId; }
    public Integer getEnergy() { return energy; }
    public void setEnergy(Integer energy) { this.energy = energy; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public Integer getCrystalCount() { return crystalCount; }
    public void setCrystalCount(Integer crystalCount) { this.crystalCount = crystalCount; }
    public Integer getDailyGainCount() { return dailyGainCount; }
    public void setDailyGainCount(Integer dailyGainCount) { this.dailyGainCount = dailyGainCount; }
    public Integer getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(Integer dailyLimit) { this.dailyLimit = dailyLimit; }
    public Boolean getDailyRedeemed() { return dailyRedeemed; }
    public void setDailyRedeemed(Boolean dailyRedeemed) { this.dailyRedeemed = dailyRedeemed; }
}
