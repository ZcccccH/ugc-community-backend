-- 用户删掉过的角色关系：「整理」不再根据设定文本把它建回来。一对角色一行，character_a_id 小于 character_b_id。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的建表语句。
CREATE TABLE IF NOT EXISTS sandbox_relation_dismissed (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  character_a_id BIGINT NOT NULL,
  character_b_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sandbox_relation_dismissed_pair (user_id, character_a_id, character_b_id)
);
