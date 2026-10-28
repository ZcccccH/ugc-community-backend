package com.ugccommunity.backend.dto.request;

/** 保存时间线。data 是整份时间线 JSON；version 是客户端读到的版本号，与当前不一致时拒绝。 */
public class SandboxTimelineRequest {
    private String data;
    private Integer version;

    public String getData(){return data;} public void setData(String v){data=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
