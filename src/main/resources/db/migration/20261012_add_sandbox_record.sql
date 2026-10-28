-- 世界设定记录与自定义类别。设定页和地图按 id 读同一条记录。
-- category：creature / plant / mineral / weather / history / building / country / city，或 custom（配合 category_id）。
-- legacy_key：本地旧数据（例如旧的植物档案）迁移的幂等键，同一用户下唯一，重复迁移不产生重复记录。
-- version：每次修改加一，带旧版本号的修改会被拒绝。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的建表语句。
CREATE TABLE IF NOT EXISTS sandbox_record_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  worldview_id BIGINT NOT NULL,
  name VARCHAR(40) NOT NULL,
  icon VARCHAR(500) NULL,
  sort_order INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_record_category_world (user_id, worldview_id)
);

CREATE TABLE IF NOT EXISTS sandbox_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  worldview_id BIGINT NOT NULL,
  category VARCHAR(20) NOT NULL,
  category_id BIGINT NULL,
  name VARCHAR(80) NOT NULL,
  description TEXT NULL,
  icon VARCHAR(500) NULL,
  display TEXT NULL,
  legacy_key VARCHAR(80) NULL,
  version INT NOT NULL DEFAULT 1,
  sort_order INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sandbox_record_legacy (user_id, legacy_key),
  KEY idx_sandbox_record_world (user_id, worldview_id, category),
  KEY idx_sandbox_record_category (category_id)
);
