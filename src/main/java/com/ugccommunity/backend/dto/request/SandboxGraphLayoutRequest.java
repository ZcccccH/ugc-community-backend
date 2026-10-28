package com.ugccommunity.backend.dto.request;

import java.util.List;

/**
 * 整体替换某个视图的排版：scope 下原有的位置全部删除，写入 items。
 * items 为空等同于清除排版（回到自动布局）。
 */
public class SandboxGraphLayoutRequest {
    /** all、world:{worldviewId} 或 org:{organizationId} */
    private String scope;
    private List<Item> items;

    public String getScope(){return scope;} public void setScope(String v){scope=v;}
    public List<Item> getItems(){return items;} public void setItems(List<Item> v){items=v;}

    public static class Item {
        private Long characterId;
        private Double x;
        private Double y;
        private Double z;
        public Long getCharacterId(){return characterId;} public void setCharacterId(Long v){characterId=v;}
        public Double getX(){return x;} public void setX(Double v){x=v;}
        public Double getY(){return y;} public void setY(Double v){y=v;}
        public Double getZ(){return z;} public void setZ(Double v){z=v;}
    }
}
