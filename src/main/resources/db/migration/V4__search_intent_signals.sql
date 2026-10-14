-- Store authenticated catalog searches as low-strength, privacy-limited interest signals.
ALTER TABLE interactions
    MODIFY COLUMN item_id BIGINT UNSIGNED NULL,
    ADD COLUMN search_query VARCHAR(120) NULL AFTER interaction_type,
    DROP CHECK chk_interactions_type,
    ADD CONSTRAINT chk_interactions_type CHECK (
        interaction_type IN ('VIEW', 'CLICK', 'READ_PROGRESS', 'SAVE', 'PURCHASE', 'LIKE', 'DISLIKE', 'HIDE',
                             'SHARE', 'SKIP', 'MORE_LIKE', 'SEARCH')
    ),
    ADD CONSTRAINT chk_interactions_search_query CHECK (
        (interaction_type = 'SEARCH' AND search_query IS NOT NULL
            AND CHAR_LENGTH(TRIM(search_query)) BETWEEN 2 AND 120)
        OR (interaction_type <> 'SEARCH' AND search_query IS NULL)
    );
