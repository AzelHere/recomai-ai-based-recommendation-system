-- Request IDs make delivery retries of one interaction request safe while preserving distinct events.
ALTER TABLE interactions
    ADD COLUMN request_id CHAR(36) NULL,
    ADD UNIQUE INDEX uk_interactions_user_request (user_id, request_id),
    ADD INDEX ix_interactions_user_item_progress (user_id, item_id, occurred_at, interaction_id);
