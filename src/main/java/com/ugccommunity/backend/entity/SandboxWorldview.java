package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sandbox_worldview")
public class SandboxWorldview {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long worldGroupId;
    public Long getWorldGroupId(){return worldGroupId;} public void setWorldGroupId(Long v){worldGroupId=v;}
    private String name;
    private String description;
    private String content;
    private Integer level;
    private Integer sortOrder;
    private Double canvasX;
    private Double canvasY;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getContent() { return content; } public void setContent(String content) { this.content = content; }
    public Integer getLevel() { return level; } public void setLevel(Integer level) { this.level = level; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Double getCanvasX() { return canvasX; } public void setCanvasX(Double canvasX) { this.canvasX = canvasX; }
    public Double getCanvasY() { return canvasY; } public void setCanvasY(Double canvasY) { this.canvasY = canvasY; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
