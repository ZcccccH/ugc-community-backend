package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一次「整理」的结果报告：新增了什么、哪些内容因为对不上或有歧义没有处理。只追加，不修改。 */
@TableName("sandbox_organize_log")
public class SandboxOrganizeLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    /** world 或 character */
    private String scope;
    private Long refId;
    private String report;
    private LocalDateTime createdAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getScope(){return scope;} public void setScope(String v){scope=v;}
    public Long getRefId(){return refId;} public void setRefId(Long v){refId=v;}
    public String getReport(){return report;} public void setReport(String v){report=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
