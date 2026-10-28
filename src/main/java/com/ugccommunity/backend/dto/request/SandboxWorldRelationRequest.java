package com.ugccommunity.backend.dto.request;

/** 新建 / 修改世界观关系。修改时 fromWorldviewId、toWorldviewId 忽略（端点不可改，需删除后重建）。 */
public class SandboxWorldRelationRequest {
    private Long fromWorldviewId;
    private Long toWorldviewId;
    private String label;
    private String reverseLabel;
    /** oneWay | twoWay，缺省 oneWay */
    private String direction;
    private String color;
    private String note;

    public Long getFromWorldviewId(){return fromWorldviewId;} public void setFromWorldviewId(Long v){fromWorldviewId=v;}
    public Long getToWorldviewId(){return toWorldviewId;} public void setToWorldviewId(Long v){toWorldviewId=v;}
    public String getLabel(){return label;} public void setLabel(String v){label=v;}
    public String getReverseLabel(){return reverseLabel;} public void setReverseLabel(String v){reverseLabel=v;}
    public String getDirection(){return direction;} public void setDirection(String v){direction=v;}
    public String getColor(){return color;} public void setColor(String v){color=v;}
    public String getNote(){return note;} public void setNote(String v){note=v;}
}
