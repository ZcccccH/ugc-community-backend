package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 世界观之间的关系（例如「平行世界」「同一宇宙」）。一对世界观最多一行，由服务层保证。 */
@TableName("sandbox_world_relation")
public class SandboxWorldRelation {
    public static final String ONE_WAY = "oneWay";
    public static final String TWO_WAY = "twoWay";

    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long fromWorldviewId;
    private Long toWorldviewId;
    private String label;
    private String reverseLabel;
    private String direction;
    private String color;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getFromWorldviewId(){return fromWorldviewId;} public void setFromWorldviewId(Long v){fromWorldviewId=v;}
    public Long getToWorldviewId(){return toWorldviewId;} public void setToWorldviewId(Long v){toWorldviewId=v;}
    public String getLabel(){return label;} public void setLabel(String v){label=v;}
    public String getReverseLabel(){return reverseLabel;} public void setReverseLabel(String v){reverseLabel=v;}
    public String getDirection(){return direction;} public void setDirection(String v){direction=v;}
    public String getColor(){return color;} public void setColor(String v){color=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}

    public boolean isTwoWay(){return TWO_WAY.equals(direction);}
    public boolean connects(Long a, Long b){
        return (a.equals(fromWorldviewId) && b.equals(toWorldviewId)) || (b.equals(fromWorldviewId) && a.equals(toWorldviewId));
    }
}
