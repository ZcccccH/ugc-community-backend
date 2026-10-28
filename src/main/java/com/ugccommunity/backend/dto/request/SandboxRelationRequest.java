package com.ugccommunity.backend.dto.request;

/** 新建 / 修改角色关系。修改时 fromCharacterId、toCharacterId 忽略（端点不可改，需删除后重建）。 */
public class SandboxRelationRequest {
    private Long fromCharacterId;
    private Long toCharacterId;
    private String label;
    private String reverseLabel;
    /** oneWay | twoWay，缺省 oneWay */
    private String direction;
    private String color;
    private Boolean arrow;
    private String note;
    /** from 角色眼中的 to / to 角色眼中的 from；不传表示不改 */
    private String fromView;
    private String toView;
    public String getFromView(){return fromView;} public void setFromView(String v){fromView=v;}
    public String getToView(){return toView;} public void setToView(String v){toView=v;}

    public Long getFromCharacterId(){return fromCharacterId;} public void setFromCharacterId(Long v){fromCharacterId=v;}
    public Long getToCharacterId(){return toCharacterId;} public void setToCharacterId(Long v){toCharacterId=v;}
    public String getLabel(){return label;} public void setLabel(String v){label=v;}
    public String getReverseLabel(){return reverseLabel;} public void setReverseLabel(String v){reverseLabel=v;}
    public String getDirection(){return direction;} public void setDirection(String v){direction=v;}
    public String getColor(){return color;} public void setColor(String v){color=v;}
    public Boolean getArrow(){return arrow;} public void setArrow(Boolean v){arrow=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
}
