package com.ugccommunity.backend.vo;

/** 作品详情里「所属合辑」一行：id、名称、看得到的作品数 */
public class WorkCollectionBriefVO {
    private Long id;
    private String name;
    private Integer workCount;

    public WorkCollectionBriefVO() {}
    public WorkCollectionBriefVO(Long id, String name, Integer workCount) { this.id = id; this.name = name; this.workCount = workCount; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getWorkCount() { return workCount; }
    public void setWorkCount(Integer workCount) { this.workCount = workCount; }
}
