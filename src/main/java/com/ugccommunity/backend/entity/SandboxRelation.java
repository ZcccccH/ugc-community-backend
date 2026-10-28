package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 箱庭角色关系边。一对角色之间最多一行：
 * direction=oneWay 表示 from→to；direction=twoWay 表示双向，label 为 from→to 的关系名，reverseLabel 为 to→from 的关系名。
 * label / reverseLabel / color / note 可以被清空：updateById 时这几列总是写入（包括 null），调用方必须先读出整行再更新。
 * source=legacy 表示由旧字段 sandbox_character.relation_character_ids 同步而来，source=app 表示由新接口创建。
 */
@TableName("sandbox_relation")
public class SandboxRelation {
    public static final String ONE_WAY = "oneWay";
    public static final String TWO_WAY = "twoWay";
    public static final String SOURCE_LEGACY = "legacy";
    public static final String SOURCE_APP = "app";

    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long fromCharacterId;
    private Long toCharacterId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String label;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String reverseLabel;
    private String direction;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String color;
    private Boolean arrow;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String note;
    /** from 角色眼中的 to（对对方的认知） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String fromView;
    /** to 角色眼中的 from */
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String toView;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getFromCharacterId(){return fromCharacterId;} public void setFromCharacterId(Long v){fromCharacterId=v;}
    public Long getToCharacterId(){return toCharacterId;} public void setToCharacterId(Long v){toCharacterId=v;}
    public String getLabel(){return label;} public void setLabel(String v){label=v;}
    public String getReverseLabel(){return reverseLabel;} public void setReverseLabel(String v){reverseLabel=v;}
    public String getDirection(){return direction;} public void setDirection(String v){direction=v;}
    public String getColor(){return color;} public void setColor(String v){color=v;}
    public Boolean getArrow(){return arrow;} public void setArrow(Boolean v){arrow=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public String getFromView(){return fromView;} public void setFromView(String v){fromView=v;}
    public String getToView(){return toView;} public void setToView(String v){toView=v;}
    public String getSource(){return source;} public void setSource(String v){source=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}

    public boolean isTwoWay(){return TWO_WAY.equals(direction);}
    public boolean isLegacy(){return SOURCE_LEGACY.equals(source);}
    /** 是否连接 a 与 b（不分方向） */
    public boolean connects(Long a, Long b){
        return (a.equals(fromCharacterId) && b.equals(toCharacterId)) || (b.equals(fromCharacterId) && a.equals(toCharacterId));
    }
}
