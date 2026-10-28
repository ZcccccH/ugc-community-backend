package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 时间线。每个世界观一条世界时间线（slot = world:{世界观 id}），每个角色一条角色时间线（slot = char:{角色 id}）。
 * data 是一份 JSON：时间锚点 nodes、事件 events、事件之间的连接 links、暗影层文字 shadows、显示设置 settings。
 * 角色时间线里的事件就是角色设定的「经历」，保存时同步写回角色卡。
 */
@TableName("sandbox_timeline")
public class SandboxTimeline {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long worldviewId;
    private Long characterId;
    private String slot;
    private String data;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getWorldviewId(){return worldviewId;} public void setWorldviewId(Long v){worldviewId=v;}
    public Long getCharacterId(){return characterId;} public void setCharacterId(Long v){characterId=v;}
    public String getSlot(){return slot;} public void setSlot(String v){slot=v;}
    public String getData(){return data;} public void setData(String v){data=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
