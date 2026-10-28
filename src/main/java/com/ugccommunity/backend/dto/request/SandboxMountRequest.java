package com.ugccommunity.backend.dto.request;
import java.util.List;
public class SandboxMountRequest {
    private List<Long> worldviewIds; private Long mainCharacterId; private List<Long> relatedCharacterIds; private Long scriptId;
    public List<Long> getWorldviewIds(){return worldviewIds;} public void setWorldviewIds(List<Long> v){worldviewIds=v;} public Long getMainCharacterId(){return mainCharacterId;} public void setMainCharacterId(Long v){mainCharacterId=v;}
    public List<Long> getRelatedCharacterIds(){return relatedCharacterIds;} public void setRelatedCharacterIds(List<Long> v){relatedCharacterIds=v;} public Long getScriptId(){return scriptId;} public void setScriptId(Long v){scriptId=v;}
}
