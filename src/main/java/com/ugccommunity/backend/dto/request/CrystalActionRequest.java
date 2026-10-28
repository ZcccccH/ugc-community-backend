package com.ugccommunity.backend.dto.request;

public class CrystalActionRequest {
    private String crystalId;
    private Integer amount;

    public String getCrystalId() { return crystalId; }
    public void setCrystalId(String crystalId) { this.crystalId = crystalId; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
}
