package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sandbox_mount")
public class SandboxMount {
    @TableId(type = IdType.INPUT) private Long userId;
    private String worldviewIds; private Long mainCharacterId; private String relatedCharacterIds; private Long scriptId; private LocalDateTime updatedAt;
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public String getWorldviewIds(){return worldviewIds;} public void setWorldviewIds(String v){worldviewIds=v;}
    public Long getMainCharacterId(){return mainCharacterId;} public void setMainCharacterId(Long v){mainCharacterId=v;} public String getRelatedCharacterIds(){return relatedCharacterIds;} public void setRelatedCharacterIds(String v){relatedCharacterIds=v;}
    public Long getScriptId(){return scriptId;} public void setScriptId(Long v){scriptId=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
