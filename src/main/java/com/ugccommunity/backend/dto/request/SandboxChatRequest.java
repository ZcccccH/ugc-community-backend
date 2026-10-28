package com.ugccommunity.backend.dto.request;

import java.util.List;

/**
 * 聊天相关请求共用。不同接口各取所需：
 * 新建 / 配置会话：title、primaryWorldviewId、secondaryWorldviewId、characterIds、mode、apiSource、prompt；
 * 发消息：content、generate；保存设备端生成的回复：content、replaceMessageId；
 * 重新生成：messageId；临时记忆：characterId、content、afterMessageId。
 */
public class SandboxChatRequest {
    private String title; private String content;
    private Long primaryWorldviewId;
    private Long secondaryWorldviewId;
    private List<Long> characterIds;
    private String mode;
    private String apiSource;
    private String prompt;
    /** 发消息时是否由平台 AI 生成回复；个人 API 的会话传 false，由设备端生成后再保存 */
    private Boolean generate;
    private Long messageId;
    private Long replaceMessageId;
    private Long characterId;
    private Long afterMessageId;
    /** 生成参数（发消息、重新生成、保存回复时可带）：是否写旁白、温度、Top P、最大输出 Token、上下文消息数量上限（0 不限制） */
    private Boolean narration;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private Integer contextLimit;

    public Boolean getNarration(){return narration;} public void setNarration(Boolean v){narration=v;}
    public Double getTemperature(){return temperature;} public void setTemperature(Double v){temperature=v;}
    public Double getTopP(){return topP;} public void setTopP(Double v){topP=v;}
    public Integer getMaxTokens(){return maxTokens;} public void setMaxTokens(Integer v){maxTokens=v;}
    public Integer getContextLimit(){return contextLimit;} public void setContextLimit(Integer v){contextLimit=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getContent(){return content;} public void setContent(String v){content=v;}
    public Long getPrimaryWorldviewId(){return primaryWorldviewId;} public void setPrimaryWorldviewId(Long v){primaryWorldviewId=v;}
    public Long getSecondaryWorldviewId(){return secondaryWorldviewId;} public void setSecondaryWorldviewId(Long v){secondaryWorldviewId=v;}
    public List<Long> getCharacterIds(){return characterIds;} public void setCharacterIds(List<Long> v){characterIds=v;}
    public String getMode(){return mode;} public void setMode(String v){mode=v;}
    public String getApiSource(){return apiSource;} public void setApiSource(String v){apiSource=v;}
    public String getPrompt(){return prompt;} public void setPrompt(String v){prompt=v;}
    public Boolean getGenerate(){return generate;} public void setGenerate(Boolean v){generate=v;}
    public Long getMessageId(){return messageId;} public void setMessageId(Long v){messageId=v;}
    public Long getReplaceMessageId(){return replaceMessageId;} public void setReplaceMessageId(Long v){replaceMessageId=v;}
    public Long getCharacterId(){return characterId;} public void setCharacterId(Long v){characterId=v;}
    public Long getAfterMessageId(){return afterMessageId;} public void setAfterMessageId(Long v){afterMessageId=v;}
}
