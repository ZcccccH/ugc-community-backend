-- 收藏别人的整个合辑（与收藏夹 favorite_collection 无关）
-- 已有合辑默认公开；作者可改为私密。AdminSchemaInitializer 启动时会执行同样的语句（已存在则跳过）。
alter table work_collection add column visibility varchar(10) not null default 'public';
alter table work_collection add column fav_count int not null default 0;
alter table work_collection add column updated_at datetime default null;
create table if not exists work_collection_favorite (
  user_id bigint not null,
  collection_id bigint not null,
  created_at datetime default current_timestamp,
  primary key (user_id, collection_id),
  key idx_work_collection_favorite_collection (collection_id)
);
