-- Record a recommendation only after its card becomes visible in a user's feed.
-- One row per persisted recommendation makes impressions idempotent across rerenders.
CREATE TABLE recommendation_impressions (
    recommendation_id BIGINT UNSIGNED NOT NULL,
    shown_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    clicked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (recommendation_id),
    INDEX ix_recommendation_impressions_shown (shown_at, recommendation_id),
    INDEX ix_recommendation_impressions_clicked (clicked_at, recommendation_id),
    CONSTRAINT fk_recommendation_impressions_recommendation FOREIGN KEY (recommendation_id)
        REFERENCES recommendations (recommendation_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
