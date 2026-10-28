package com.ugccommunity.backend.vo;

import java.util.ArrayList;
import java.util.List;

public class PaidUnlockEarningsVO {
    private Integer totalUnlocks;
    private Integer totalCrystalAmount;
    private List<PaidUnlockWorkEarningVO> works = new ArrayList<PaidUnlockWorkEarningVO>();

    public Integer getTotalUnlocks() { return totalUnlocks; }
    public void setTotalUnlocks(Integer totalUnlocks) { this.totalUnlocks = totalUnlocks; }
    public Integer getTotalCrystalAmount() { return totalCrystalAmount; }
    public void setTotalCrystalAmount(Integer totalCrystalAmount) { this.totalCrystalAmount = totalCrystalAmount; }
    public List<PaidUnlockWorkEarningVO> getWorks() { return works; }
    public void setWorks(List<PaidUnlockWorkEarningVO> works) { this.works = works; }
}
