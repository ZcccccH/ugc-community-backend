package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户删掉过的世界关系（不分方向，worldviewAId 小于 worldviewBId）。
 * 「整理」看到这一对世界观时不再根据设定文本把关系建回来；用户自己重新建立关系时这条记录删除。
 */
@TableName("sandbox_world_relation_dismissed")
public class SandboxWorldRelationDismissed {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long worldviewAId;
    private Long worldviewBId;
    private LocalDateTime createdAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getWorldviewAId(){return worldviewAId;} public void setWorldviewAId(Long v){worldviewAId=v;}
    public Long getWorldviewBId(){return worldviewBId;} public void setWorldviewBId(Long v){worldviewBId=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}

    /** 是否是 a 与 b 这一对（不分方向） */
    public boolean connects(Long a, Long b){
        return (a.equals(worldviewAId) && b.equals(worldviewBId)) || (b.equals(worldviewAId) && a.equals(worldviewBId));
    }
}
