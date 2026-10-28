package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 临时记忆：在一段对话的某个位置给某个角色加的记忆，只对这段对话里该位置之后的生成有效。
 * 不写入角色或世界观的永久设定，也不影响其它对话。
 */
@TableName("sandbox_chat_memory")
public class SandboxChatMemory {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long sessionId;
    private Long characterId;
    private String content;
    /** 生效位置：只有排在这个位置之后的回复生成时才带上这条记忆 */
    private Long afterSortKey;
    private LocalDateTime createdAt;

    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public Long getSessionId(){return sessionId;} public void setSessionId(Long v){sessionId=v;}
    public Long getCharacterId(){return characterId;} public void setCharacterId(Long v){characterId=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
    public Long getAfterSortKey(){return afterSortKey;} public void setAfterSortKey(Long v){afterSortKey=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
