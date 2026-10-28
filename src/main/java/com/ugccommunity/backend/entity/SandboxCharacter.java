package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sandbox_character")
public class SandboxCharacter {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private Long worldviewId; private Long organizationId; private String worldviewIds; private String organizationIds; private Long folderId; private Long counterpartCharacterId; private String relationCharacterIds; private String cardUrl; private String name; private String avatar;
    private String description; private String personality; private String background; private String content; private String relationLabel; private String relationColor; private Boolean relationArrow; private String relationArrowStyle; private Integer sortOrder; private Double canvasX; private Double canvasY; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getWorldviewId(){return worldviewId;} public void setWorldviewId(Long v){worldviewId=v;} public Long getOrganizationId(){return organizationId;} public void setOrganizationId(Long v){organizationId=v;} public String getWorldviewIds(){return worldviewIds;} public void setWorldviewIds(String v){worldviewIds=v;} public String getOrganizationIds(){return organizationIds;} public void setOrganizationIds(String v){organizationIds=v;}
    public String getName(){return name;} public void setName(String v){name=v;} public String getAvatar(){return avatar;} public void setAvatar(String v){avatar=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;} public String getPersonality(){return personality;} public void setPersonality(String v){personality=v;}
    public String getBackground(){return background;} public void setBackground(String v){background=v;} public String getContent(){return content;} public void setContent(String v){content=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
    public Long getCounterpartCharacterId(){return counterpartCharacterId;} public void setCounterpartCharacterId(Long v){counterpartCharacterId=v;} public String getRelationCharacterIds(){return relationCharacterIds;} public void setRelationCharacterIds(String v){relationCharacterIds=v;} public String getCardUrl(){return cardUrl;} public void setCardUrl(String v){cardUrl=v;}
    public String getRelationLabel(){return relationLabel;} public void setRelationLabel(String v){relationLabel=v;}
    public String getRelationColor(){return relationColor;} public void setRelationColor(String v){relationColor=v;} public Boolean getRelationArrow(){return relationArrow;} public void setRelationArrow(Boolean v){relationArrow=v;} public String getRelationArrowStyle(){return relationArrowStyle;} public void setRelationArrowStyle(String v){relationArrowStyle=v;}
    /** 角色档案文件夹；null = 未归档。普通的保存角色不改它（更新时跳过 null），移动走 /characters/{id}/folder */
    public Long getFolderId(){return folderId;} public void setFolderId(Long v){folderId=v;}
    public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;} public Double getCanvasX(){return canvasX;} public void setCanvasX(Double v){canvasX=v;} public Double getCanvasY(){return canvasY;} public void setCanvasY(Double v){canvasY=v;}
}
