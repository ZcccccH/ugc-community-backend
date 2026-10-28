package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户删掉过的角色关系（不分方向，characterAId 小于 characterBId）。
 * 「整理」看到这一对角色时不再根据设定文本把关系建回来；用户自己重新建立关系时这条记录删除。
 */
@TableName("sandbox_relation_dismissed")
public class SandboxRelationDismissed {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long characterAId;
    private Long characterBId;
    private LocalDateTime createdAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getCharacterAId(){return characterAId;} public void setCharacterAId(Long v){characterAId=v;}
    public Long getCharacterBId(){return characterBId;} public void setCharacterBId(Long v){characterBId=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}

    /** 是否是 a 与 b 这一对（不分方向） */
    public boolean connects(Long a, Long b){
        return (a.equals(characterAId) && b.equals(characterBId)) || (b.equals(characterAId) && a.equals(characterBId));
    }
}
