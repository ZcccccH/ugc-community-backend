ALTER TABLE work
    ADD INDEX idx_work_feed_latest (status, is_deleted, type, created_at);

ALTER TABLE work
    ADD INDEX idx_work_feed_hot (status, is_deleted, type, view_count, like_count, fav_count, created_at);

ALTER TABLE work
    ADD INDEX idx_work_following_feed (user_id, status, is_deleted, created_at);

ALTER TABLE work_tag
    ADD INDEX idx_work_tag_tag_work (tag_id, work_id);

ALTER TABLE tag
    ADD INDEX idx_tag_hot (is_deleted, is_disabled, usage_count, id);

ALTER TABLE message
    ADD INDEX idx_message_user_read_created (user_id, is_read, created_at);
