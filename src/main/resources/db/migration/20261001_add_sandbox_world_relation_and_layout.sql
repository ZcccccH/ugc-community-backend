-- 世界观之间的关系。一对世界观之间最多一行（由 SandboxWorldRelationService 保证）。
-- direction: oneWay = from→to；twoWay = 双向，label 为 from→to，reverse_label 为 to→from。
-- 关系网排版：用户自动排版或拖动后保存的角色位置。scope = all（全部角色）或 world:{世界观ID}。
-- 应用启动时 SandboxSchemaInitializer 也会执行同样的建表语句。
CREATE TABLE IF NOT EXISTS sandbox_world_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  from_worldview_id BIGINT NOT NULL,
  to_worldview_id BIGINT NOT NULL,
  label VARCHAR(100) NULL,
  reverse_label VARCHAR(100) NULL,
  direction VARCHAR(10) NOT NULL DEFAULT 'oneWay',
  color VARCHAR(20) NULL,
  note VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_world_relation_user (user_id)
);

CREATE TABLE IF NOT EXISTS sandbox_graph_layout (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  scope VARCHAR(40) NOT NULL,
  character_id BIGINT NOT NULL,
  x DOUBLE NOT NULL,
  y DOUBLE NOT NULL,
  z DOUBLE NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sandbox_graph_layout (user_id, scope, character_id),
  KEY idx_sandbox_graph_layout_character (character_id)
);
