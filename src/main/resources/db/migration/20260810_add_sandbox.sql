CREATE TABLE IF NOT EXISTS sandbox_worldview (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, name VARCHAR(80) NOT NULL, description TEXT, content TEXT, level INT, sort_order INT, canvas_x DOUBLE, canvas_y DOUBLE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_worldview_user (user_id, updated_at)
);
CREATE TABLE IF NOT EXISTS sandbox_organization (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, worldview_id BIGINT, name VARCHAR(80) NOT NULL, description TEXT, sort_order INT, canvas_x DOUBLE, canvas_y DOUBLE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_org_user_world (user_id, worldview_id)
);
CREATE TABLE IF NOT EXISTS sandbox_character (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, worldview_id BIGINT, organization_id BIGINT, worldview_ids TEXT, organization_ids TEXT, name VARCHAR(80) NOT NULL,
  avatar VARCHAR(500), description TEXT, personality TEXT, background TEXT, counterpart_character_id BIGINT,
  relation_character_ids TEXT, relation_label VARCHAR(200), relation_color VARCHAR(20), relation_arrow TINYINT, relation_arrow_style VARCHAR(40), card_url VARCHAR(1000), sort_order INT, canvas_x DOUBLE, canvas_y DOUBLE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_character_user_world (user_id, worldview_id), KEY idx_sandbox_character_org (organization_id)
);
CREATE TABLE IF NOT EXISTS sandbox_script (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, worldview_id BIGINT, name VARCHAR(80) NOT NULL, description TEXT, content MEDIUMTEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_script_user_world (user_id, worldview_id)
);
CREATE TABLE IF NOT EXISTS sandbox_mount (
  user_id BIGINT PRIMARY KEY, worldview_ids TEXT, main_character_id BIGINT, related_character_ids TEXT, script_id BIGINT,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS sandbox_chat_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, title VARCHAR(120) NOT NULL, worldview_ids TEXT, main_character_id BIGINT,
  related_character_ids TEXT, script_id BIGINT, status VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sandbox_chat_session_user (user_id, updated_at)
);
CREATE TABLE IF NOT EXISTS sandbox_chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id BIGINT NOT NULL, user_id BIGINT NOT NULL, role VARCHAR(20) NOT NULL, content TEXT NOT NULL,
  mock_reply TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sandbox_chat_message_session (session_id, created_at), KEY idx_sandbox_chat_message_user (user_id)
);
