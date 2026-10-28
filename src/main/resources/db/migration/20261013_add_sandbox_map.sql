-- 地图：每个世界观一张世界地图（parent_map_id 为空，slot = root），
-- 世界地图上的区域可链接一张区域地图（slot = 父地图 id:区域 key）。
-- terrain：高程与水域栅格；overlay：区域边界与标记（JSON）；view：显示选项（JSON）。背景图只存地址。
-- version：每次修改加一，带旧版本号的修改会被拒绝。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的建表语句。
CREATE TABLE IF NOT EXISTS sandbox_map (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  worldview_id BIGINT NOT NULL,
  parent_map_id BIGINT NULL,
  parent_region_key VARCHAR(64) NULL,
  slot VARCHAR(100) NOT NULL,
  name VARCHAR(80) NOT NULL,
  background_url VARCHAR(500) NULL,
  terrain MEDIUMTEXT NULL,
  overlay MEDIUMTEXT NULL,
  view TEXT NULL,
  version INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sandbox_map_slot (worldview_id, slot),
  KEY idx_sandbox_map_user_world (user_id, worldview_id),
  KEY idx_sandbox_map_parent (parent_map_id)
);
