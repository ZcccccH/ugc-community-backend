-- 角色档案文件夹：只整理角色列表，与世界观、组织无关。删除文件夹时角色的 folder_id 清空（回到未归档）。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的语句。
CREATE TABLE IF NOT EXISTS sandbox_character_folder (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(80) NOT NULL,
  sort_order INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_character_folder_user (user_id)
);
ALTER TABLE sandbox_character ADD COLUMN folder_id BIGINT NULL;
