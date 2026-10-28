-- Adds image display options to `work`.
-- Already included in sql/schema.sql; only needed when upgrading an older database.
ALTER TABLE work
  ADD COLUMN allow_image_save TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether readers may save images (0 = no)',
  ADD COLUMN image_display_mode VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'Image display mode: normal / long';
