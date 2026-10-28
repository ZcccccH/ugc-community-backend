-- 用户删掉过的世界关系：「整理」不再根据设定文本把它建回来。一对世界观一行，worldview_a_id 小于 worldview_b_id。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的建表语句。
CREATE TABLE IF NOT EXISTS sandbox_world_relation_dismissed (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  worldview_a_id BIGINT NOT NULL,
  worldview_b_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sandbox_world_relation_dismissed_pair (user_id, worldview_a_id, worldview_b_id)
);
