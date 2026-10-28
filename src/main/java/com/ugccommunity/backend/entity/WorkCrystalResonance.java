package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("work_crystal_resonance")
public class WorkCrystalResonance {
    @TableId(type = IdType.INPUT)
    private Long workId;
    private String crystalId;
    private Integer count;
    private LocalDateTime updatedAt;

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }
    public String getCrystalId() { return crystalId; }
    public void setCrystalId(String crystalId) { this.crystalId = crystalId; }
    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
