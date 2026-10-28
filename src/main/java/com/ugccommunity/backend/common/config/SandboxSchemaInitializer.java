package com.ugccommunity.backend.common.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/** Ensures the sandbox MVP tables exist in deployments that do not run Flyway. */
@Component
public class SandboxSchemaInitializer {
    private final JdbcTemplate jdbcTemplate;

    public SandboxSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        execute("create table if not exists sandbox_worldview (id bigint primary key auto_increment, user_id bigint not null, name varchar(80) not null, description text, content text, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_worldview_user (user_id, updated_at))");
        execute("alter table sandbox_worldview add column content text null");
        execute("alter table sandbox_worldview add column level int null");
        execute("alter table sandbox_worldview add column sort_order int null");
        execute("alter table sandbox_worldview add column canvas_x double null");
        execute("alter table sandbox_worldview add column canvas_y double null");
        jdbcTemplate.execute("create table if not exists sandbox_world_group (id bigint primary key auto_increment, user_id bigint not null, name varchar(80) not null, legacy_world_id bigint null, unique key uk_world_group_legacy (legacy_world_id), key idx_world_group_user (user_id))");
        execute("alter table sandbox_worldview add column world_group_id bigint null");
        execute("alter table sandbox_world_group add column main_world_id bigint null");
        jdbcTemplate.execute("insert into sandbox_world_group (user_id,name,legacy_world_id) select w.user_id,w.name,w.id from sandbox_worldview w where w.world_group_id is null and not exists (select 1 from sandbox_world_group g where g.legacy_world_id=w.id)");
        jdbcTemplate.execute("update sandbox_worldview w set world_group_id=(select g.id from sandbox_world_group g where g.legacy_world_id=w.id and g.user_id=w.user_id) where w.world_group_id is null and exists (select 1 from sandbox_world_group g where g.legacy_world_id=w.id and g.user_id=w.user_id)");
        execute("create table if not exists sandbox_organization (id bigint primary key auto_increment, user_id bigint not null, worldview_id bigint, name varchar(80) not null, description text, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_org_user_world (user_id, worldview_id))");
        execute("alter table sandbox_organization add column sort_order int null");
        execute("alter table sandbox_organization add column canvas_x double null");
        execute("alter table sandbox_organization add column canvas_y double null");
        execute("alter table sandbox_organization add column content text null");
        execute("alter table sandbox_organization add column relation_organization_ids text null");
        execute("alter table sandbox_organization add column relation_label varchar(200) null");
        execute("alter table sandbox_organization add column relation_color varchar(20) null");
        execute("alter table sandbox_organization add column relation_arrow tinyint null");
        execute("alter table sandbox_organization add column relation_arrow_style varchar(40) null");
        execute("create table if not exists sandbox_character (id bigint primary key auto_increment, user_id bigint not null, worldview_id bigint, organization_id bigint, counterpart_character_id bigint, relation_character_ids text, card_url varchar(1000), name varchar(80) not null, avatar varchar(500), description text, personality text, background text, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_character_user_world (user_id, worldview_id), key idx_sandbox_character_org (organization_id))");
        execute("alter table sandbox_character add column counterpart_character_id bigint null");
        execute("alter table sandbox_character add column relation_character_ids text null");
        execute("alter table sandbox_character add column relation_label varchar(200) null");
        execute("alter table sandbox_character add column relation_color varchar(20) null");
        execute("alter table sandbox_character add column relation_arrow tinyint null");
        execute("alter table sandbox_character add column relation_arrow_style varchar(40) null");
        execute("alter table sandbox_character add column worldview_ids text null");
        execute("alter table sandbox_character add column organization_ids text null");
        execute("alter table sandbox_character add column card_url varchar(1000) null");
        execute("alter table sandbox_character add column sort_order int null");
        execute("alter table sandbox_character add column canvas_x double null");
        execute("alter table sandbox_character add column canvas_y double null");
        // The nine "basic profile" fields of a character (name/age/height/appearance/traits/race/abilities/immunities/resistances) are stored here as JSON; lists and details do not show the raw text.
        execute("alter table sandbox_character add column content text null");
        // Character folders: only organize the character list, independent of worldviews and organizations; folder_id NULL = unfiled
        execute("create table if not exists sandbox_character_folder (id bigint primary key auto_increment, user_id bigint not null, name varchar(80) not null, sort_order int null, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_character_folder_user (user_id))");
        execute("alter table sandbox_character add column folder_id bigint null");
        execute("create table if not exists sandbox_script (id bigint primary key auto_increment, user_id bigint not null, worldview_id bigint, name varchar(80) not null, description text, content mediumtext, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_script_user_world (user_id, worldview_id))");
        execute("alter table sandbox_script add column description text null");
        execute("create table if not exists sandbox_mount (user_id bigint primary key, worldview_ids text, main_character_id bigint, related_character_ids text, script_id bigint, updated_at datetime not null default current_timestamp on update current_timestamp)");
        execute("create table if not exists sandbox_chat_session (id bigint primary key auto_increment, user_id bigint not null, title varchar(120) not null, worldview_ids text, main_character_id bigint, related_character_ids text, script_id bigint, status varchar(20) not null default 'active', created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_chat_session_user (user_id, updated_at))");
        execute("create table if not exists sandbox_chat_message (id bigint primary key auto_increment, session_id bigint not null, user_id bigint not null, role varchar(20) not null, content text not null, mock_reply tinyint not null default 0, created_at datetime not null default current_timestamp, key idx_sandbox_chat_message_session (session_id, created_at), key idx_sandbox_chat_message_user (user_id))");
        // Character relations: at most one row per character pair (enforced by the service layer); source=legacy means it was synced from the old relation_character_ids field
        execute("create table if not exists sandbox_relation (id bigint primary key auto_increment, user_id bigint not null, from_character_id bigint not null, to_character_id bigint not null, label varchar(100) null, reverse_label varchar(100) null, direction varchar(10) not null default 'oneWay', color varchar(20) null, arrow tinyint null, note varchar(500) null, source varchar(10) not null default 'app', created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_relation_user (user_id), key idx_sandbox_relation_from (from_character_id), key idx_sandbox_relation_to (to_character_id))");
        // Perception of the other character: how "from" sees "to" and how "to" sees "from"
        execute("alter table sandbox_relation add column from_view varchar(500) null");
        execute("alter table sandbox_relation add column to_view varchar(500) null");
        // Relations between worldviews: at most one row per worldview pair (enforced by the service layer)
        execute("create table if not exists sandbox_world_relation (id bigint primary key auto_increment, user_id bigint not null, from_worldview_id bigint not null, to_worldview_id bigint not null, label varchar(100) null, reverse_label varchar(100) null, direction varchar(10) not null default 'oneWay', color varchar(20) null, note varchar(500) null, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_world_relation_user (user_id))");
        // Relation graph layout: scope = all or world:{id}; one row per user, view and character
        execute("create table if not exists sandbox_graph_layout (id bigint primary key auto_increment, user_id bigint not null, scope varchar(40) not null, character_id bigint not null, x double not null, y double not null, z double not null default 0, updated_at datetime not null default current_timestamp on update current_timestamp, unique key uk_sandbox_graph_layout (user_id, scope, character_id), key idx_sandbox_graph_layout_character (character_id))");
        // World setting records and custom categories; legacy_key makes migration of old local data re-runnable without duplicates
        execute("create table if not exists sandbox_record_category (id bigint primary key auto_increment, user_id bigint not null, worldview_id bigint not null, name varchar(40) not null, icon varchar(500) null, sort_order int null, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, key idx_sandbox_record_category_world (user_id, worldview_id))");
        execute("create table if not exists sandbox_record (id bigint primary key auto_increment, user_id bigint not null, worldview_id bigint not null, category varchar(20) not null, category_id bigint null, name varchar(80) not null, description text null, icon varchar(500) null, display text null, legacy_key varchar(80) null, version int not null default 1, sort_order int null, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, unique key uk_sandbox_record_legacy (user_id, legacy_key), key idx_sandbox_record_world (user_id, worldview_id, category), key idx_sandbox_record_category (category_id))");
        // Maps: world map + region maps; slot guarantees one world map per worldview and one map per region
        execute("create table if not exists sandbox_map (id bigint primary key auto_increment, user_id bigint not null, worldview_id bigint not null, parent_map_id bigint null, parent_region_key varchar(64) null, slot varchar(100) not null, name varchar(80) not null, background_url varchar(500) null, terrain mediumtext null, overlay mediumtext null, view text null, version int not null default 1, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, unique key uk_sandbox_map_slot (worldview_id, slot), key idx_sandbox_map_user_world (user_id, worldview_id), key idx_sandbox_map_parent (parent_map_id))");
        // Timelines: one world timeline per worldview and one per character; data holds the whole timeline as JSON
        execute("create table if not exists sandbox_timeline (id bigint primary key auto_increment, user_id bigint not null, worldview_id bigint null, character_id bigint null, slot varchar(40) not null, data mediumtext null, version int not null default 1, created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp on update current_timestamp, unique key uk_sandbox_timeline_slot (user_id, slot), key idx_sandbox_timeline_world (worldview_id), key idx_sandbox_timeline_character (character_id))");
        // "Organize" result reports: append-only
        execute("create table if not exists sandbox_organize_log (id bigint primary key auto_increment, user_id bigint not null, scope varchar(20) not null, ref_id bigint not null, report mediumtext null, created_at datetime not null default current_timestamp, key idx_sandbox_organize_log_ref (user_id, scope, ref_id, id))");
        // Character relations deleted by the user: "Organize" will not recreate them
        execute("create table if not exists sandbox_relation_dismissed (id bigint primary key auto_increment, user_id bigint not null, character_a_id bigint not null, character_b_id bigint not null, created_at datetime not null default current_timestamp, unique key uk_sandbox_relation_dismissed_pair (user_id, character_a_id, character_b_id))");
        // World relations deleted by the user: "Organize" will not recreate them
        execute("create table if not exists sandbox_world_relation_dismissed (id bigint primary key auto_increment, user_id bigint not null, worldview_a_id bigint not null, worldview_b_id bigint not null, created_at datetime not null default current_timestamp, unique key uk_sandbox_world_relation_dismissed_pair (user_id, worldview_a_id, worldview_b_id))");
        // Chat: session config (worldview, characters, mode, prompt), message speaker and ordering, short-term memory
        execute("alter table sandbox_chat_session add column primary_worldview_id bigint null");
        execute("alter table sandbox_chat_session add column secondary_worldview_id bigint null");
        execute("alter table sandbox_chat_session add column character_ids text null");
        execute("alter table sandbox_chat_session add column mode varchar(20) null");
        execute("alter table sandbox_chat_session add column mode_prompt text null");
        execute("alter table sandbox_chat_session add column prompt_applied tinyint null");
        execute("alter table sandbox_chat_session add column api_source varchar(20) null");
        execute("alter table sandbox_chat_message add column kind varchar(20) null");
        execute("alter table sandbox_chat_message add column speaker_character_id bigint null");
        execute("alter table sandbox_chat_message add column speaker_name varchar(80) null");
        execute("alter table sandbox_chat_message add column reply_group bigint null");
        execute("alter table sandbox_chat_message add column sort_key bigint null");
        execute("alter table sandbox_chat_message add column edited tinyint null");
        execute("create table if not exists sandbox_chat_memory (id bigint primary key auto_increment, user_id bigint not null, session_id bigint not null, character_id bigint not null, content text not null, after_sort_key bigint not null default 0, created_at datetime not null default current_timestamp, key idx_sandbox_chat_memory_session (session_id, after_sort_key))");
        // Old clients read JSON null as the string "null" and saved it back: reset those values to NULL (re-runnable)
        for (String column : new String[]{"relation_label", "relation_color", "relation_arrow_style"}) {
            execute("update sandbox_character set " + column + "=null where " + column + "='null'");
            execute("update sandbox_organization set " + column + "=null where " + column + "='null'");
        }
    }

    private void execute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
            // Keep startup compatible with partially provisioned or read-only environments.
        }
    }
}
