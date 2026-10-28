package com.ugccommunity.backend.dto.request;

/** 新建 / 修改自定义设定类别。修改时 worldviewId 忽略。 */
public class SandboxRecordCategoryRequest {
    private Long worldviewId;
    private String name;
    private String icon;
    private Integer sortOrder;

    public Long getWorldviewId(){return worldviewId;} public void setWorldviewId(Long v){worldviewId=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getIcon(){return icon;} public void setIcon(String v){icon=v;}
    public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;}
}
