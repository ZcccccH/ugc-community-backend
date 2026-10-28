package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 角色档案文件夹：只用来整理角色列表，和世界观、组织都没有关系。
 * 一个角色最多在一个文件夹里（sandbox_character.folder_id），没有文件夹的角色显示为「未归档」。
 */
@TableName("sandbox_character_folder")
public class SandboxCharacterFolder {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
