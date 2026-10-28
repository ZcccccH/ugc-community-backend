-- 「整理」结果报告：每次整理追加一行，保留没有处理的内容（对不上的名字、有歧义的名字、原文找不到依据的条目）。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的建表语句。
CREATE TABLE IF NOT EXISTS sandbox_organize_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  scope VARCHAR(20) NOT NULL,
  ref_id BIGINT NOT NULL,
  report MEDIUMTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sandbox_organize_log_ref (user_id, scope, ref_id, id)
);
