-- Persist the ranker's actual weighted feature contributions for transparent API and UI rendering.
-- Existing recommendation rows are retained; their historical breakdown remains NULL.
ALTER TABLE recommendations
    ADD COLUMN explanation_json JSON NULL AFTER algorithm_version;

ALTER TABLE recommendations
    DROP CHECK chk_recommendations_reason_type,
    ADD CONSTRAINT chk_recommendations_reason_type CHECK (
        reason_type IN ('ONBOARDING', 'PROFILE_MATCH', 'RECENT_ACTIVITY', 'RELATED_INTEREST', 'DISCOVERY',
                        'COLD_START', 'POPULARITY', 'COLLABORATIVE', 'RATING_AFFINITY', 'CONTENT_SIMILARITY')
    );

CREATE INDEX ix_recommendations_algorithm_time
    ON recommendations (algorithm_version, generated_at);

CREATE INDEX ix_interactions_type_recent_item_user
    ON interactions (interaction_type, occurred_at, item_id, user_id);

CREATE INDEX ix_interactions_user_type_recent_item
    ON interactions (user_id, interaction_type, occurred_at, item_id);
