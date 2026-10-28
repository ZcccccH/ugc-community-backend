-- 时间线：每个世界观一条世界时间线（slot = world:{世界观 id}），每个角色一条角色时间线（slot = char:{角色 id}）。
-- data：整份时间线 JSON（时间锚点、事件、连接、暗影层文字、显示设置）。
-- version：每次修改加一，带旧版本号的修改会被拒绝。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的建表语句。
CREATE TABLE IF NOT EXISTS sandbox_timeline (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  worldview_id BIGINT NULL,
  character_id BIGINT NULL,
  slot VARCHAR(40) NOT NULL,
  data MEDIUMTEXT NULL,
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sandbox_timeline_slot (user_id, slot),
  KEY idx_sandbox_timeline_world (worldview_id),
  KEY idx_sandbox_timeline_character (character_id)
);
