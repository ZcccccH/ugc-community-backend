package com.ugccommunity.backend.vo;

public class CatStateVO {
    private String catId;
    private Double satiety;
    private String colorCrystalId;
    private Integer feedCount;

    public String getCatId() { return catId; }
    public void setCatId(String catId) { this.catId = catId; }
    public Double getSatiety() { return satiety; }
    public void setSatiety(Double satiety) { this.satiety = satiety; }
    public String getColorCrystalId() { return colorCrystalId; }
    public void setColorCrystalId(String colorCrystalId) { this.colorCrystalId = colorCrystalId; }
    public Integer getFeedCount() { return feedCount; }
    public void setFeedCount(Integer feedCount) { this.feedCount = feedCount; }
}
