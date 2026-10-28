-- 聊天会话配置、消息说话者与临时记忆。
-- 会话：主世界观、补充世界观（最多两个）、所选角色、模式、模式提示词及是否已应用、AI 来源。
-- 消息：类型（user / narration / character）、说话角色、同一次回复的分组、排序位置、是否被编辑过。
-- 旧会话和旧消息的新列为空：旧会话可查看，配置并应用提示词之前不能继续生成。
-- 下面的 ALTER 不可重复执行；应用启动时 SandboxSchemaInitializer 会逐条执行并忽略「列已存在」。
ALTER TABLE sandbox_chat_session ADD COLUMN primary_worldview_id BIGINT NULL;
ALTER TABLE sandbox_chat_session ADD COLUMN secondary_worldview_id BIGINT NULL;
ALTER TABLE sandbox_chat_session ADD COLUMN character_ids TEXT NULL;
ALTER TABLE sandbox_chat_session ADD COLUMN mode VARCHAR(20) NULL;
ALTER TABLE sandbox_chat_session ADD COLUMN mode_prompt TEXT NULL;
ALTER TABLE sandbox_chat_session ADD COLUMN prompt_applied TINYINT NULL;
ALTER TABLE sandbox_chat_session ADD COLUMN api_source VARCHAR(20) NULL;
ALTER TABLE sandbox_chat_message ADD COLUMN kind VARCHAR(20) NULL;
ALTER TABLE sandbox_chat_message ADD COLUMN speaker_character_id BIGINT NULL;
ALTER TABLE sandbox_chat_message ADD COLUMN speaker_name VARCHAR(80) NULL;
ALTER TABLE sandbox_chat_message ADD COLUMN reply_group BIGINT NULL;
ALTER TABLE sandbox_chat_message ADD COLUMN sort_key BIGINT NULL;
ALTER TABLE sandbox_chat_message ADD COLUMN edited TINYINT NULL;

CREATE TABLE IF NOT EXISTS sandbox_chat_memory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  session_id BIGINT NOT NULL,
  character_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  after_sort_key BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sandbox_chat_memory_session (session_id, after_sort_key)
);
