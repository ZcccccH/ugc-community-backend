package com.ugccommunity.backend.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
/**
 * 箱庭聊天消息。一次 AI 回复按说话者拆成多行：旁白一行、每个角色一行，同一次回复的各行 replyGroup 和 sortKey 相同。
 * 消息按 (sortKey, id) 排序；重新生成时新内容沿用原来的 sortKey，所以留在原位置，不影响后面的消息。
 * 旧数据的 kind / sortKey 为空：读取时 sortKey 按 id × 1000 计。
 */
@TableName("sandbox_chat_message")
public class SandboxChatMessage {
    public static final String KIND_USER = "user";
    public static final String KIND_NARRATION = "narration";
    public static final String KIND_CHARACTER = "character";

    @TableId(type = IdType.AUTO) private Long id;
    private Long sessionId; private Long userId; private String role; private String content; private Integer mockReply; private LocalDateTime createdAt;
    private String kind;
    private Long speakerCharacterId;
    private String speakerName;
    private Long replyGroup;
    private Long sortKey;
    private Integer edited;

    public Long getId(){return id;} public void setId(Long v){id=v;} public Long getSessionId(){return sessionId;} public void setSessionId(Long v){sessionId=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getRole(){return role;} public void setRole(String v){role=v;} public String getContent(){return content;} public void setContent(String v){content=v;} public Integer getMockReply(){return mockReply;} public void setMockReply(Integer v){mockReply=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public String getKind(){return kind;} public void setKind(String v){kind=v;}
    public Long getSpeakerCharacterId(){return speakerCharacterId;} public void setSpeakerCharacterId(Long v){speakerCharacterId=v;}
    public String getSpeakerName(){return speakerName;} public void setSpeakerName(String v){speakerName=v;}
    public Long getReplyGroup(){return replyGroup;} public void setReplyGroup(Long v){replyGroup=v;}
    public Long getSortKey(){return sortKey;} public void setSortKey(Long v){sortKey=v;}
    public Integer getEdited(){return edited;} public void setEdited(Integer v){edited=v;}

    /** 排序位置：新消息用 sortKey，旧消息按 id 推算 */
    public long position(){return sortKey != null ? sortKey : (id == null ? 0L : id * 1000L);}
}
