package com.ugccommunity.backend.common.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class AdminSchemaInitializer {
    private final JdbcTemplate jdbcTemplate;

    public AdminSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            jdbcTemplate.execute("alter table ban_word add column is_deleted int default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table user add column privacy_agreed int default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table user add column background_image varchar(255) default 'asset_bg'");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table user add column last_active_at datetime default null");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table work add column edit_count int default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table work add column audit_reason varchar(255) default null");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table work add column allow_image_save tinyint not null default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table work add column image_display_mode varchar(20) not null default 'normal'");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table work add column paid_locked tinyint not null default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table work add column unlock_crystal_id varchar(30) default 'solid'");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table work add column unlock_crystal_amount int not null default 1");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table tag add column created_by bigint default null");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table tag add column is_deleted tinyint default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_tag_favorite (user_id bigint not null, tag_id bigint not null, created_at datetime default current_timestamp, primary key (user_id, tag_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_tag_block (user_id bigint not null, tag_id bigint not null, created_at datetime default current_timestamp, primary key (user_id, tag_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_block (user_id bigint not null, blocked_user_id bigint not null, created_at datetime default current_timestamp, primary key (user_id, blocked_user_id), index idx_user_block_blocked_user (blocked_user_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists work_collection (id bigint primary key auto_increment, user_id bigint not null, name varchar(30) not null, is_deleted tinyint default 0, created_at datetime default current_timestamp)");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists work_collection_item (collection_id bigint not null, work_id bigint not null, created_at datetime default current_timestamp, primary key (collection_id, work_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists favorite_collection (id bigint primary key auto_increment, user_id bigint not null, name varchar(64) not null, is_deleted tinyint not null default 0, created_at datetime default current_timestamp, updated_at datetime default current_timestamp on update current_timestamp, unique key uk_favorite_collection_user_name_active (user_id, name, is_deleted), key idx_favorite_collection_user (user_id, is_deleted, created_at))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists favorite_collection_item (collection_id bigint not null, user_id bigint not null, work_id bigint not null, created_at datetime default current_timestamp, primary key (collection_id, work_id), key idx_fav_collection_item_user_work (user_id, work_id), key idx_fav_collection_item_collection (collection_id, created_at))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_feedback (id bigint primary key auto_increment, user_id bigint not null, content varchar(500) not null, status varchar(20) not null default 'pending', reply varchar(500) default null, handled_by bigint default null, handled_at datetime default null, created_at datetime default current_timestamp)");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists work_comment (id bigint primary key auto_increment, work_id bigint not null, user_id bigint not null, content varchar(80) not null, created_at datetime default current_timestamp)");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_pin (user_id bigint not null, target_type varchar(20) not null, target_id bigint not null, sort_order int not null default 0, created_at datetime not null default current_timestamp, primary key (user_id, target_type, target_id), index idx_user_pin_type_sort (user_id, target_type, sort_order, created_at), index idx_user_pin_target (target_type, target_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_view_history (user_id bigint not null, work_id bigint not null, viewed_at datetime not null default current_timestamp, primary key (user_id, work_id), index idx_user_view_history_time (user_id, viewed_at), index idx_user_view_history_work (work_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_achievement (user_id bigint not null, achievement_id varchar(50) not null, progress int not null default 0, unlocked tinyint not null default 0, unlocked_at datetime default null, updated_at datetime not null default current_timestamp on update current_timestamp, primary key (user_id, achievement_id), index idx_user_achievement_unlocked (user_id, unlocked))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_crystal_state (user_id bigint not null, crystal_id varchar(30) not null, energy int not null default 0, crystal_count int not null default 0, daily_gain_count int not null default 0, daily_gain_date date default null, daily_redeem_date date default null, active tinyint not null default 0, updated_at datetime not null default current_timestamp on update current_timestamp, primary key (user_id, crystal_id), index idx_user_crystal_active (user_id, active))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table user_crystal_state add column crystal_count int not null default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table user_crystal_state add column daily_gain_count int not null default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table user_crystal_state add column daily_gain_date date default null");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table user_crystal_state add column daily_redeem_date date default null");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists work_crystal_resonance (work_id bigint not null, crystal_id varchar(30) not null, count int not null default 0, updated_at datetime not null default current_timestamp on update current_timestamp, primary key (work_id, crystal_id), index idx_work_crystal (work_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_work_resonance (user_id bigint not null, work_id bigint not null, crystal_id varchar(30) not null, created_at datetime not null default current_timestamp, primary key (user_id, work_id), index idx_user_work_resonance_work (work_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_work_unlock (user_id bigint not null, work_id bigint not null, crystal_id varchar(30) not null, crystal_amount int not null, created_at datetime not null default current_timestamp, primary key (user_id, work_id), index idx_user_work_unlock_work (work_id))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists user_cat_state (user_id bigint not null, cat_id varchar(30) not null, satiety decimal(5,2) not null default 0.00, color_crystal_id varchar(30) not null default 'solid', feed_count int not null default 0, updated_at datetime not null default current_timestamp on update current_timestamp, primary key (user_id, cat_id), index idx_user_cat_feed (user_id, feed_count))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists system_setting (config_key varchar(64) not null primary key, config_value varchar(512) default null, updated_at datetime not null default current_timestamp on update current_timestamp)");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists app_identity (id bigint primary key auto_increment, app_name varchar(100) not null, app_key varchar(64) not null, app_secret varchar(128) not null, rsa_public_key text, rsa_private_key text, status tinyint not null default 1, created_at datetime not null default current_timestamp, unique key uk_app_identity_key (app_key))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists app_release (id bigint primary key auto_increment, app_key varchar(64) not null, os varchar(20) not null, variant varchar(50) default null, version_display varchar(50) not null, version_code int not null default 1, file_size varchar(30) default null, download_url varchar(500) default null, changelog text, force_update tinyint not null default 0, enabled tinyint not null default 1, sign_enabled tinyint not null default 0, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, index idx_app_release_lookup (app_key, os, enabled, version_code))");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table app_release add column sign_enabled tinyint not null default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table app_release add column sign_max_skew_seconds int not null default 300");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table app_release add column sign_nonce_ttl_seconds int not null default 600");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table app_release add column csrf_enabled tinyint not null default 0");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table app_release add column csrf_secret varchar(128) default null");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("alter table app_release add column csrf_token_ttl_seconds int not null default 1800");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("create table if not exists api_request_log (id bigint primary key auto_increment, request_time datetime(3) not null, duration int default null, method varchar(10) not null, path varchar(500) not null, query_string varchar(2000) default null, client_ip varchar(64) not null, x_forwarded_for varchar(500) default null, region varchar(100) default null, user_id bigint default null, app_key varchar(64) default null, user_agent varchar(500) default null, device_type varchar(20) default null, os_name varchar(50) default null, browser_name varchar(50) default null, http_status int default null, status varchar(20) default 'success', request_headers text default null, request_body text default null, threat_tag varchar(30) default 'normal', request_id varchar(64) default null, index idx_api_log_time (request_time), index idx_api_log_ip (client_ip, request_time), index idx_api_log_threat (threat_tag, request_time), index idx_api_log_user (user_id, request_time))");
        } catch (Exception ignored) {
        }
        // Collections favorited from other users: visibility, favorite count, update time, and the favorite record table (all statements are re-runnable)
        for (String sql : new String[]{
                "alter table work_collection add column visibility varchar(10) not null default 'public'",
                "alter table work_collection add column fav_count int not null default 0",
                "alter table work_collection add column updated_at datetime default null",
                "create table if not exists work_collection_favorite (user_id bigint not null, collection_id bigint not null, created_at datetime default current_timestamp, primary key (user_id, collection_id), key idx_work_collection_favorite_collection (collection_id))"
        }) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception ignored) {
            }
        }
    }
}
