-- Add explicit completion events and durable named collections for Phase 2 UX.
ALTER TABLE interactions
    DROP CHECK chk_interactions_type,
    ADD CONSTRAINT chk_interactions_type CHECK (
        interaction_type IN ('VIEW', 'CLICK', 'READ_PROGRESS', 'SAVE', 'PURCHASE', 'LIKE', 'DISLIKE', 'HIDE',
                             'SHARE', 'SKIP', 'MORE_LIKE', 'SEARCH', 'COMPLETE')
    );

CREATE TABLE user_collections (
    collection_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id),
    UNIQUE INDEX uk_user_collections_name (user_id, name),
    INDEX ix_user_collections_user_time (user_id, updated_at),
    CONSTRAINT fk_user_collections_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_collection_items (
    collection_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, item_id),
    INDEX ix_user_collection_items_item (item_id),
    CONSTRAINT fk_user_collection_items_collection FOREIGN KEY (collection_id)
        REFERENCES user_collections (collection_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_user_collection_items_item FOREIGN KEY (item_id)
        REFERENCES items (item_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
