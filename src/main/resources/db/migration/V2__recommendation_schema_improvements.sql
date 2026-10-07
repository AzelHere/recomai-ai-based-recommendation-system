-- Additive changes for databases created from V1.
-- Existing rows remain valid: new lifecycle columns use safe defaults and batch_id is nullable.

ALTER TABLE categories
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD INDEX ix_categories_active_name (is_active, name);

ALTER TABLE interests
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER name,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER is_active,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD INDEX ix_interests_active_name (is_active, name);

ALTER TABLE items
    DROP CHECK chk_items_type,
    ADD CONSTRAINT chk_items_type CHECK (
        item_type IN ('PRODUCT', 'CONTENT', 'COURSE', 'RESOURCE', 'ARTICLE', 'TOOL', 'PROJECT', 'OTHER')
    );

ALTER TABLE interactions
    DROP CHECK chk_interactions_type,
    ADD CONSTRAINT chk_interactions_type CHECK (
        interaction_type IN ('VIEW', 'CLICK', 'SAVE', 'PURCHASE', 'LIKE', 'DISLIKE', 'HIDE')
    );

CREATE TABLE recommendation_batches (
    batch_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL DEFAULT 'content-score-v1',
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (batch_id),
    UNIQUE INDEX uk_recommendation_batches_batch_user (batch_id, user_id),
    INDEX ix_recommendation_batches_user_time (user_id, generated_at, batch_id),
    CONSTRAINT fk_recommendation_batches_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE recommendations
    ADD COLUMN batch_id BIGINT UNSIGNED NULL AFTER recommendation_id,
    ADD UNIQUE INDEX uk_recommendations_batch_item (batch_id, item_id),
    ADD CONSTRAINT fk_recommendations_batch_user FOREIGN KEY (batch_id, user_id)
        REFERENCES recommendation_batches (batch_id, user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    DROP CHECK chk_recommendations_score,
    ADD CONSTRAINT chk_recommendations_score CHECK (score BETWEEN 0 AND 100);
