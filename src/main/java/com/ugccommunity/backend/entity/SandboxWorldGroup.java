package com.ugccommunity.backend.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("sandbox_world_group")
public class SandboxWorldGroup {
 @TableId(type=IdType.AUTO) private Long id;
 private Long userId;
 private String name;
 private Long legacyWorldId;
 /** 主世界；为空时取这个世界观里 id 最小的世界 */
 private Long mainWorldId;
 public Long getId(){return id;} public void setId(Long v){id=v;}
 public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
 public String getName(){return name;} public void setName(String v){name=v;}
 public Long getLegacyWorldId(){return legacyWorldId;} public void setLegacyWorldId(Long v){legacyWorldId=v;}
 public Long getMainWorldId(){return mainWorldId;} public void setMainWorldId(Long v){mainWorldId=v;}
}
