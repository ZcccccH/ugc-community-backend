-- UGC Community Backend: database schema (structure only, no data).
-- Usage: mysql -u "$DB_USERNAME" -p ugc_community < sql/schema.sql
-- Later incremental changes live in src/main/resources/db/migration and are also
-- applied idempotently on startup by the schema initializers.

CREATE DATABASE IF NOT EXISTS `ugc_community` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `ugc_community`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `api_request_log`;
CREATE TABLE `api_request_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_time` datetime(3) NOT NULL,
  `duration` int DEFAULT NULL,
  `method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `path` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `query_string` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `client_ip` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `x_forwarded_for` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `region` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `app_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `os_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `browser_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `http_status` int DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'success',
  `request_headers` text COLLATE utf8mb4_unicode_ci,
  `request_body` text COLLATE utf8mb4_unicode_ci,
  `threat_tag` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT 'normal',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_api_log_time` (`request_time`),
  KEY `idx_api_log_ip` (`client_ip`,`request_time`),
  KEY `idx_api_log_threat` (`threat_tag`,`request_time`),
  KEY `idx_api_log_user` (`user_id`,`request_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `app_identity`;
CREATE TABLE `app_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `app_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `app_secret` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rsa_public_key` text COLLATE utf8mb4_unicode_ci,
  `rsa_private_key` text COLLATE utf8mb4_unicode_ci,
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_identity_key` (`app_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `app_release`;
CREATE TABLE `app_release` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `os` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `variant` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version_display` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version_code` int NOT NULL DEFAULT '1',
  `file_size` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `download_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `changelog` text COLLATE utf8mb4_unicode_ci,
  `force_update` tinyint NOT NULL DEFAULT '0',
  `enabled` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `sign_enabled` tinyint NOT NULL DEFAULT '0',
  `sign_max_skew_seconds` int NOT NULL DEFAULT '300',
  `sign_nonce_ttl_seconds` int NOT NULL DEFAULT '600',
  `csrf_enabled` tinyint NOT NULL DEFAULT '0',
  `csrf_secret` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `csrf_token_ttl_seconds` int NOT NULL DEFAULT '1800',
  PRIMARY KEY (`id`),
  KEY `idx_app_release_lookup` (`app_key`,`os`,`enabled`,`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `ban_word`;
CREATE TABLE `ban_word` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `word` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '违禁词',
  `level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high',
  `category` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类',
  `created_by` bigint DEFAULT NULL COMMENT '创建管理员ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ban_word` (`word`),
  KEY `fk_ban_word_created_by` (`created_by`),
  CONSTRAINT `fk_ban_word_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='违禁词库';

DROP TABLE IF EXISTS `favorite_collection`;
CREATE TABLE `favorite_collection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_collection_user_name_active` (`user_id`,`name`,`is_deleted`),
  KEY `idx_favorite_collection_user` (`user_id`,`is_deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `favorite_collection_item`;
CREATE TABLE `favorite_collection_item` (
  `collection_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`collection_id`,`work_id`),
  KEY `idx_fav_collection_item_user_work` (`user_id`,`work_id`),
  KEY `idx_fav_collection_item_collection` (`collection_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'like/favorite/follow/system/audit',
  `title` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息标题',
  `content` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '消息内容',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '0-未读 1-已读',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_message_user_read` (`user_id`,`is_read`),
  CONSTRAINT `fk_message_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

DROP TABLE IF EXISTS `report`;
CREATE TABLE `report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `reporter_id` bigint NOT NULL COMMENT '举报人ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `reason` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '举报原因',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `audit_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核说明',
  `handled_by` bigint DEFAULT NULL COMMENT '处理管理员ID',
  `handled_at` datetime DEFAULT NULL COMMENT '处理时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_report_status` (`status`),
  KEY `idx_report_work` (`work_id`),
  KEY `fk_report_reporter` (`reporter_id`),
  KEY `fk_report_handled_by` (`handled_by`),
  CONSTRAINT `fk_report_handled_by` FOREIGN KEY (`handled_by`) REFERENCES `user` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_work` FOREIGN KEY (`work_id`) REFERENCES `work` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报处理表';

DROP TABLE IF EXISTS `system_setting`;
CREATE TABLE `system_setting` (
  `config_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_value` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名',
  `usage_count` int NOT NULL DEFAULT '0' COMMENT '使用次数',
  `is_disabled` tinyint NOT NULL DEFAULT '0' COMMENT '0-启用 1-禁用',
  `disabled_by` bigint DEFAULT NULL COMMENT '禁用管理员ID',
  `disabled_at` datetime DEFAULT NULL COMMENT '禁用时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` bigint DEFAULT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`),
  KEY `idx_tag_disabled` (`is_disabled`),
  KEY `fk_tag_disabled_by` (`disabled_by`),
  CONSTRAINT `fk_tag_disabled_by` FOREIGN KEY (`disabled_by`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码，演示环境使用明文，后续服务层可改BCrypt',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '昵称',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像资源名或URL',
  `gender` tinyint NOT NULL DEFAULT '0' COMMENT '0-人类 1-古兽',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT 'user/admin',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-正常 0-拉黑/禁用',
  `phone` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `bio` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '个人简介',
  `follow_count` int NOT NULL DEFAULT '0' COMMENT '关注数',
  `fans_count` int NOT NULL DEFAULT '0' COMMENT '粉丝数',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '获赞数',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0-未删除 1-已删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `privacy_agreed` int DEFAULT '0',
  `background_image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT 'asset_bg',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_phone` (`phone`),
  UNIQUE KEY `uk_user_email` (`email`),
  KEY `idx_user_status` (`status`),
  KEY `idx_user_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

DROP TABLE IF EXISTS `user_achievement`;
CREATE TABLE `user_achievement` (
  `user_id` bigint NOT NULL,
  `achievement_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress` int NOT NULL DEFAULT '0',
  `unlocked` tinyint NOT NULL DEFAULT '0',
  `unlocked_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`achievement_id`),
  KEY `idx_user_achievement_unlocked` (`user_id`,`unlocked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_cat_state`;
CREATE TABLE `user_cat_state` (
  `user_id` bigint NOT NULL,
  `cat_id` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `satiety` decimal(5,2) NOT NULL DEFAULT '0.00',
  `color_crystal_id` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'solid',
  `feed_count` int NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`cat_id`),
  KEY `idx_user_cat_feed` (`user_id`,`feed_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_crystal_state`;
CREATE TABLE `user_crystal_state` (
  `user_id` bigint NOT NULL,
  `crystal_id` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `energy` int NOT NULL DEFAULT '0',
  `active` tinyint NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`crystal_id`),
  KEY `idx_user_crystal_active` (`user_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_favorite`;
CREATE TABLE `user_favorite` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_id`,`work_id`),
  KEY `idx_favorite_work` (`work_id`),
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_favorite_work` FOREIGN KEY (`work_id`) REFERENCES `work` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

DROP TABLE IF EXISTS `user_feedback`;
CREATE TABLE `user_feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `reply` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handled_by` bigint DEFAULT NULL,
  `handled_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_feedback_user` (`user_id`),
  KEY `idx_feedback_status_created` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_follow`;
CREATE TABLE `user_follow` (
  `user_id` bigint NOT NULL COMMENT '关注人ID',
  `target_user_id` bigint NOT NULL COMMENT '被关注人ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_id`,`target_user_id`),
  KEY `idx_follow_target` (`target_user_id`),
  CONSTRAINT `fk_follow_target_user` FOREIGN KEY (`target_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_follow_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注表';

DROP TABLE IF EXISTS `user_like`;
CREATE TABLE `user_like` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_id`,`work_id`),
  KEY `idx_like_work` (`work_id`),
  CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_like_work` FOREIGN KEY (`work_id`) REFERENCES `work` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户点赞表';

DROP TABLE IF EXISTS `user_pin`;
CREATE TABLE `user_pin` (
  `user_id` bigint NOT NULL,
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`target_type`,`target_id`),
  KEY `idx_user_pin_type_sort` (`user_id`,`target_type`,`sort_order`,`created_at`),
  KEY `idx_user_pin_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_tag_block`;
CREATE TABLE `user_tag_block` (
  `user_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户拉黑Tag';

DROP TABLE IF EXISTS `user_tag_favorite`;
CREATE TABLE `user_tag_favorite` (
  `user_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`tag_id`),
  KEY `idx_user_tag_favorite_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_view_history`;
CREATE TABLE `user_view_history` (
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `viewed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`work_id`),
  KEY `idx_user_view_history_time` (`user_id`,`viewed_at`),
  KEY `idx_user_view_history_work` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_work_resonance`;
CREATE TABLE `user_work_resonance` (
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `crystal_id` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`work_id`),
  KEY `idx_user_work_resonance_work` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `work`;
CREATE TABLE `work` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '作者ID',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'image/video/article',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '正文/描述',
  `cover` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面资源名或URL',
  `media_url` text COLLATE utf8mb4_unicode_ci COMMENT '媒体资源URL',
  `duration` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '音视频时长',
  `location` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地点',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'published' COMMENT 'draft/published/hidden/deleted',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `fav_count` int NOT NULL DEFAULT '0' COMMENT '收藏数',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览数',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0-未删除 1-已删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `edit_count` int DEFAULT '0',
  `audit_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `allow_image_save` tinyint NOT NULL DEFAULT '0',
  `image_display_mode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'normal',
  PRIMARY KEY (`id`),
  KEY `idx_work_user` (`user_id`),
  KEY `idx_work_type_status` (`type`,`status`),
  KEY `idx_work_created` (`created_at`),
  FULLTEXT KEY `ft_work_title_content` (`title`,`content`),
  CONSTRAINT `fk_work_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品表';

DROP TABLE IF EXISTS `work_collection`;
CREATE TABLE `work_collection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_deleted` tinyint DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `visibility` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'public',
  `fav_count` int NOT NULL DEFAULT '0',
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_collection_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户作品合辑';

DROP TABLE IF EXISTS `work_collection_favorite`;
CREATE TABLE `work_collection_favorite` (
  `user_id` bigint NOT NULL,
  `collection_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`collection_id`),
  KEY `idx_work_collection_favorite_collection` (`collection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏别人的合辑';

DROP TABLE IF EXISTS `work_collection_item`;
CREATE TABLE `work_collection_item` (
  `collection_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`collection_id`,`work_id`),
  KEY `idx_collection_item_work` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合辑作品关联';

DROP TABLE IF EXISTS `work_comment`;
CREATE TABLE `work_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `content` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comment_work_created` (`work_id`,`created_at`),
  KEY `idx_comment_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `work_crystal_resonance`;
CREATE TABLE `work_crystal_resonance` (
  `work_id` bigint NOT NULL,
  `crystal_id` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `count` int NOT NULL DEFAULT '0',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`work_id`,`crystal_id`),
  KEY `idx_work_crystal` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `work_tag`;
CREATE TABLE `work_tag` (
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`work_id`,`tag_id`),
  KEY `idx_work_tag_tag` (`tag_id`),
  CONSTRAINT `fk_work_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_work_tag_work` FOREIGN KEY (`work_id`) REFERENCES `work` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品标签关联表';

SET FOREIGN_KEY_CHECKS = 1;
