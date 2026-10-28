package com.ugccommunity.backend.dto.request;

/** 新建 / 修改地图。修改时 worldviewId、parentMapId、parentRegionKey 忽略；为 null 的字段不改。 */
public class SandboxMapRequest {
    private Long worldviewId;
    /** 为空表示世界地图 */
    private Long parentMapId;
    private String parentRegionKey;
    private String name;
    private String backgroundUrl;
    private String terrain;
    private String overlay;
    private String view;
    /** 仅修改：客户端读到的版本号；与当前不一致时拒绝 */
    private Integer version;

    public Long getWorldviewId(){return worldviewId;} public void setWorldviewId(Long v){worldviewId=v;}
    public Long getParentMapId(){return parentMapId;} public void setParentMapId(Long v){parentMapId=v;}
    public String getParentRegionKey(){return parentRegionKey;} public void setParentRegionKey(String v){parentRegionKey=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getBackgroundUrl(){return backgroundUrl;} public void setBackgroundUrl(String v){backgroundUrl=v;}
    public String getTerrain(){return terrain;} public void setTerrain(String v){terrain=v;}
    public String getOverlay(){return overlay;} public void setOverlay(String v){overlay=v;}
    public String getView(){return view;} public void setView(String v){view=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;}
}
