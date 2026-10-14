-- Marked resources are editorially selected and may seed a cold-start feed.
ALTER TABLE items
    ADD COLUMN is_editor_pick BOOLEAN NOT NULL DEFAULT FALSE AFTER quality_score,
    ADD INDEX ix_items_editor_pick_active (is_editor_pick, is_active, category_id);

ALTER TABLE recommendations
    DROP CHECK chk_recommendations_reason_type,
    ADD CONSTRAINT chk_recommendations_reason_type CHECK (
        reason_type IN ('ONBOARDING', 'PROFILE_MATCH', 'RECENT_ACTIVITY', 'RELATED_INTEREST', 'DISCOVERY',
                        'COLD_START', 'POPULARITY', 'COLLABORATIVE', 'RATING_AFFINITY', 'CONTENT_SIMILARITY',
                        'EDITOR_PICK')
    );
