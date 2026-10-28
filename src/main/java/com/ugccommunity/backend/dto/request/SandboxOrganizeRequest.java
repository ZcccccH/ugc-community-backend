package com.ugccommunity.backend.dto.request;

/** 对一个世界观或一个角色当前保存的设定文本执行「整理」。 */
public class SandboxOrganizeRequest {
    /** world 或 character */
    private String scope;
    private Long id;

    public String getScope(){return scope;} public void setScope(String v){scope=v;}
    public Long getId(){return id;} public void setId(Long v){id=v;}
}
