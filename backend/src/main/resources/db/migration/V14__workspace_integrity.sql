-- Append-only: retain all existing history and catalog rows.
CREATE INDEX ix_interactions_user_order ON interactions(user_id, occurred_at DESC, interaction_id DESC);
CREATE INDEX ix_interactions_user_item_type ON interactions(user_id, item_id, interaction_type);
ALTER TABLE items ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
CREATE TABLE recommendation_settings_revision (
    singleton_id INT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_settings_singleton CHECK (singleton_id = 1)
);
INSERT INTO recommendation_settings_revision(singleton_id, revision) VALUES (1, 0);
