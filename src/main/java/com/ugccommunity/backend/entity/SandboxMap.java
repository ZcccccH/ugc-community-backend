package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 地图。每个世界观一张世界地图（parentMapId 为空），世界地图上的每个区域可以链接一张区域地图
 * （parentMapId + parentRegionKey 指向父地图上的那个区域）。
 * 地形、叠加层（区域边界、标记）和显示选项各自成列，和导入的背景图分开保存。
 */
@TableName("sandbox_map")
public class SandboxMap {
    /** 世界地图的 slot；区域地图的 slot 是「父地图 id:区域 key」 */
    public static final String ROOT_SLOT = "root";

    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long worldviewId;
    private Long parentMapId;
    private String parentRegionKey;
    /** 同一世界观内唯一，保证一个世界观只有一张世界地图、一个区域只有一张区域地图 */
    private String slot;
    private String name;
    private String backgroundUrl;
    /** 高程与水域栅格，客户端编码的文本 */
    private String terrain;
    /** 区域边界与标记，JSON */
    private String overlay;
    /** 显示选项（等高线、等温线、各类别是否显示等），JSON */
    private String view;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getWorldviewId(){return worldviewId;} public void setWorldviewId(Long v){worldviewId=v;}
    public Long getParentMapId(){return parentMapId;} public void setParentMapId(Long v){parentMapId=v;}
    public String getParentRegionKey(){return parentRegionKey;} public void setParentRegionKey(String v){parentRegionKey=v;}
    public String getSlot(){return slot;} public void setSlot(String v){slot=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getBackgroundUrl(){return backgroundUrl;} public void setBackgroundUrl(String v){backgroundUrl=v;}
    public String getTerrain(){return terrain;} public void setTerrain(String v){terrain=v;}
    public String getOverlay(){return overlay;} public void setOverlay(String v){overlay=v;}
    public String getView(){return view;} public void setView(String v){view=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
