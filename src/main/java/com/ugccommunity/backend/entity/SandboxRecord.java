package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 世界设定记录（生物、植物、矿物、天气、历史事件、建筑、国家、城市，或用户自定义类别）。
 * 设定页和地图按 id 读同一条记录，不各存一份。
 */
@TableName("sandbox_record")
public class SandboxRecord {
    /** 用户自定义类别：此时 categoryId 指向 sandbox_record_category */
    public static final String CUSTOM = "custom";

    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long worldviewId;
    private String category;
    private Long categoryId;
    private String name;
    private String description;
    private String icon;
    /** 显示配置 JSON（颜色、是否显示名称／图标等），由客户端解释 */
    private String display;
    /** 本地旧数据迁移用的幂等键；同一用户下唯一，重复迁移不会产生重复记录 */
    private String legacyKey;
    /** 每次修改加一；带旧版本号的修改会被拒绝，避免迟到的结果覆盖新的手动修改 */
    private Integer version;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getWorldviewId(){return worldviewId;} public void setWorldviewId(Long v){worldviewId=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public Long getCategoryId(){return categoryId;} public void setCategoryId(Long v){categoryId=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getIcon(){return icon;} public void setIcon(String v){icon=v;}
    public String getDisplay(){return display;} public void setDisplay(String v){display=v;}
    public String getLegacyKey(){return legacyKey;} public void setLegacyKey(String v){legacyKey=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
