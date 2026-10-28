-- Additive: main world of a worldview folder. NULL means the folder's lowest-id world.
alter table sandbox_world_group add column main_world_id bigint null;
