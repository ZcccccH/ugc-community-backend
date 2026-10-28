package com.ugccommunity.backend.vo;

public class PaidUnlockWorkEarningVO {
    private Long workId;
    private String title;
    private Integer unlockCount;
    private Integer crystalAmount;

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getUnlockCount() { return unlockCount; }
    public void setUnlockCount(Integer unlockCount) { this.unlockCount = unlockCount; }
    public Integer getCrystalAmount() { return crystalAmount; }
    public void setCrystalAmount(Integer crystalAmount) { this.crystalAmount = crystalAmount; }
}
