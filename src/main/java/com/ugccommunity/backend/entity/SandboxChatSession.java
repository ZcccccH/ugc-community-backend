package com.ugccommunity.backend.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
/**
 * 箱庭聊天会话。新会话的配置：一个主世界观、可选的一个补充世界观（最多两个）、所选角色、模式和模式提示词。
 * worldviewIds / mainCharacterId / relatedCharacterIds / scriptId 是旧版「挂载」留下的字段，只为旧数据保留。
 * mode 为空的是旧会话：可以查看，配置好并应用提示词之前不能继续生成。
 */
@TableName("sandbox_chat_session")
public class SandboxChatSession {
    public static final String MODE_STORY = "story";
    public static final String MODE_ROLEPLAY = "roleplay";
    public static final String SOURCE_SERVER = "server";
    public static final String SOURCE_PERSONAL = "personal";

    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private String title; private String worldviewIds; private Long mainCharacterId; private String relatedCharacterIds; private Long scriptId; private String status; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    private Long primaryWorldviewId;
    private Long secondaryWorldviewId;
    /** 所选角色 id，逗号分隔 */
    private String characterIds;
    private String mode;
    private String modePrompt;
    /** 1 = 模式提示词已应用，可以开始生成 */
    private Integer promptApplied;
    /** server = 平台 AI；personal = 用户自己的 API（在设备上调用，密钥不经过服务器） */
    private String apiSource;

    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getWorldviewIds(){return worldviewIds;} public void setWorldviewIds(String v){worldviewIds=v;} public Long getMainCharacterId(){return mainCharacterId;} public void setMainCharacterId(Long v){mainCharacterId=v;}
    public String getRelatedCharacterIds(){return relatedCharacterIds;} public void setRelatedCharacterIds(String v){relatedCharacterIds=v;} public Long getScriptId(){return scriptId;} public void setScriptId(Long v){scriptId=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
    public Long getPrimaryWorldviewId(){return primaryWorldviewId;} public void setPrimaryWorldviewId(Long v){primaryWorldviewId=v;}
    public Long getSecondaryWorldviewId(){return secondaryWorldviewId;} public void setSecondaryWorldviewId(Long v){secondaryWorldviewId=v;}
    public String getCharacterIds(){return characterIds;} public void setCharacterIds(String v){characterIds=v;}
    public String getMode(){return mode;} public void setMode(String v){mode=v;}
    public String getModePrompt(){return modePrompt;} public void setModePrompt(String v){modePrompt=v;}
    public Integer getPromptApplied(){return promptApplied;} public void setPromptApplied(Integer v){promptApplied=v;}
    public String getApiSource(){return apiSource;} public void setApiSource(String v){apiSource=v;}
}
