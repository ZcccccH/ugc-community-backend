package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sandbox_organization")
public class SandboxOrganization {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private Long worldviewId; private String name; private String description;
    // content：组织设定画布的自由 JSON（蜂巢点亮格子+颜色等），不在列表/详情展示，仅供画布读写。
    private String content;
    // 组织关系：连向的其它组织 id 列表 + 共享的关系文本/颜色/箭头样式，语义与角色的 relation* 字段一致。
    private String relationOrganizationIds; private String relationLabel; private String relationColor; private Boolean relationArrow; private String relationArrowStyle;
    private Integer sortOrder; private Double canvasX; private Double canvasY;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getWorldviewId(){return worldviewId;} public void setWorldviewId(Long v){worldviewId=v;} public String getName(){return name;} public void setName(String v){name=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public String getRelationOrganizationIds(){return relationOrganizationIds;} public void setRelationOrganizationIds(String v){relationOrganizationIds=v;}
    public String getRelationLabel(){return relationLabel;} public void setRelationLabel(String v){relationLabel=v;}
    public String getRelationColor(){return relationColor;} public void setRelationColor(String v){relationColor=v;}
    public Boolean getRelationArrow(){return relationArrow;} public void setRelationArrow(Boolean v){relationArrow=v;} public String getRelationArrowStyle(){return relationArrowStyle;} public void setRelationArrowStyle(String v){relationArrowStyle=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
    public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;} public Double getCanvasX(){return canvasX;} public void setCanvasX(Double v){canvasX=v;} public Double getCanvasY(){return canvasY;} public void setCanvasY(Double v){canvasY=v;}
}
