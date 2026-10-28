CREATE TABLE IF NOT EXISTS favorite_collection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_favorite_collection_user_name_active (user_id, name, is_deleted),
    KEY idx_favorite_collection_user (user_id, is_deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS favorite_collection_item (
    collection_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    work_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, work_id),
    KEY idx_fav_collection_item_user_work (user_id, work_id),
    KEY idx_fav_collection_item_collection (collection_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
