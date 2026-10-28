package com.ugccommunity.backend.dto.request;

/** 新建 / 修改世界设定记录。修改时 worldviewId、legacyKey 忽略。 */
public class SandboxRecordRequest {
    private Long worldviewId;
    /** 内置类别键，或 custom（此时必须带 categoryId） */
    private String category;
    private Long categoryId;
    private String name;
    private String description;
    private String icon;
    private String display;
    /** 仅新建：本地旧数据迁移的幂等键，重复提交返回已有记录 */
    private String legacyKey;
    /** 仅修改：客户端读到的版本号；与当前不一致时拒绝 */
    private Integer version;
    private Integer sortOrder;

    public Long getWorldviewId(){return worldviewId;} public void setWorldviewId(Long v){worldviewId=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public Long getCategoryId(){return categoryId;} public void setCategoryId(Long v){categoryId=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getIcon(){return icon;} public void setIcon(String v){icon=v;}
    public String getDisplay(){return display;} public void setDisplay(String v){display=v;}
    public String getLegacyKey(){return legacyKey;} public void setLegacyKey(String v){legacyKey=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
    public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;}
}
