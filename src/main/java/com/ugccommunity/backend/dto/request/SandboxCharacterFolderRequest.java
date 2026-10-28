package com.ugccommunity.backend.dto.request;

/** 新建 / 重命名文件夹用 name；移动角色用 folderId（null = 移回未归档） */
public class SandboxCharacterFolderRequest {
    private String name;
    private Long folderId;
    public String getName(){return name;} public void setName(String v){name=v;}
    public Long getFolderId(){return folderId;} public void setFolderId(Long v){folderId=v;}
}
