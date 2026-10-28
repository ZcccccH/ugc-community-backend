package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 关系网里保存的角色位置（自动排版或手动拖动后的结果）。
 * scope：all = 「全部角色」视图；world:{id} = 某个世界观的视图；org:{id} = 组织详情（成员卡片）。同一用户、同一 scope、同一角色只有一行。
 * x / y 是 2D 平面坐标，z 是 3D 深度；2D 视图只用 x / y，3D 视图三个都用。
 */
@TableName("sandbox_graph_layout")
public class SandboxGraphLayout {
    public static final String SCOPE_ALL = "all";
    public static final String SCOPE_WORLD_PREFIX = "world:";
    public static final String SCOPE_ORGANIZATION_PREFIX = "org:";

    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String scope;
    private Long characterId;
    private Double x;
    private Double y;
    private Double z;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getScope(){return scope;} public void setScope(String v){scope=v;}
    public Long getCharacterId(){return characterId;} public void setCharacterId(Long v){characterId=v;}
    public Double getX(){return x;} public void setX(Double v){x=v;}
    public Double getY(){return y;} public void setY(Double v){y=v;}
    public Double getZ(){return z;} public void setZ(Double v){z=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}

    public static String worldScope(Long worldviewId){return SCOPE_WORLD_PREFIX + worldviewId;}
    public static String organizationScope(Long organizationId){return SCOPE_ORGANIZATION_PREFIX + organizationId;}
}
