-- 箱庭角色关系表。一对角色之间最多一行（由 SandboxRelationService 保证）。
-- direction: oneWay = from→to；twoWay = 双向，label 为 from→to，reverse_label 为 to→from。
-- source: legacy = 由旧字段 sandbox_character.relation_character_ids 同步而来；app = 新接口创建或编辑过。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的建表语句；旧数据在用户首次读取关系时按用户懒回填。
CREATE TABLE IF NOT EXISTS sandbox_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  from_character_id BIGINT NOT NULL,
  to_character_id BIGINT NOT NULL,
  label VARCHAR(100) NULL,
  reverse_label VARCHAR(100) NULL,
  direction VARCHAR(10) NOT NULL DEFAULT 'oneWay',
  color VARCHAR(20) NULL,
  arrow TINYINT NULL,
  note VARCHAR(500) NULL,
  source VARCHAR(10) NOT NULL DEFAULT 'app',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_relation_user (user_id),
  KEY idx_sandbox_relation_from (from_character_id),
  KEY idx_sandbox_relation_to (to_character_id)
);
