-- Additive migration: preserves all existing worlds and character links.
create table if not exists sandbox_world_group (id bigint primary key auto_increment, user_id bigint not null, name varchar(80) not null, legacy_world_id bigint null, unique key uk_world_group_legacy (legacy_world_id), key idx_world_group_user (user_id));
alter table sandbox_worldview add column world_group_id bigint null;
insert into sandbox_world_group (user_id,name,legacy_world_id) select w.user_id,w.name,w.id from sandbox_worldview w where w.world_group_id is null and not exists (select 1 from sandbox_world_group g where g.legacy_world_id=w.id);
update sandbox_worldview w set world_group_id=(select g.id from sandbox_world_group g where g.legacy_world_id=w.id and g.user_id=w.user_id) where w.world_group_id is null and exists (select 1 from sandbox_world_group g where g.legacy_world_id=w.id and g.user_id=w.user_id);
