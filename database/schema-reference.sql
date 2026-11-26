-- Current schema reference for MySQL 8.0.16+.
-- Runtime upgrades are managed by versioned scripts in db/migration.
-- The database itself must exist before the application starts.
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exploration TINYINT UNSIGNED NOT NULL DEFAULT 50,
    CONSTRAINT chk_users_exploration CHECK (exploration BETWEEN 0 AND 100),
    username VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS categories (
    category_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    niche_code VARCHAR(40) NULL,
    description VARCHAR(500) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (category_id),
    INDEX ix_categories_active_name (is_active, name),
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_niche_code UNIQUE (niche_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS interests (
    interest_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    category_id BIGINT UNSIGNED NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (interest_id),
    INDEX ix_interests_active_name (is_active, name),
    INDEX ix_interests_category_active (category_id, is_active, name),
    CONSTRAINT fk_interests_category FOREIGN KEY (category_id)
        REFERENCES categories (category_id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT uk_interests_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS items (
    item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    category_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NULL,
    item_type VARCHAR(20) NOT NULL DEFAULT 'CONTENT',
    external_url VARCHAR(2048) NULL,
    source_name VARCHAR(160) NULL,
    difficulty VARCHAR(20) NOT NULL DEFAULT 'ALL_LEVELS',
    estimated_minutes SMALLINT UNSIGNED NULL,
    quality_score DECIMAL(5, 4) NOT NULL DEFAULT 0.5000,
    is_editor_pick BOOLEAN NOT NULL DEFAULT FALSE,
    price DECIMAL(10, 2) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id),
    INDEX ix_items_category_active (category_id, is_active),
    INDEX ix_items_type_active (item_type, is_active),
    INDEX ix_items_editor_pick_active (is_editor_pick, is_active, category_id),
    CONSTRAINT chk_items_type CHECK (
        item_type IN ('PRODUCT', 'CONTENT', 'COURSE', 'RESOURCE', 'ARTICLE', 'TOOL', 'PROJECT', 'OTHER',
                      'VIDEO', 'DOCUMENTATION', 'GUIDE')
    ),
    CONSTRAINT chk_items_difficulty CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'ALL_LEVELS')),
    CONSTRAINT chk_items_estimated_minutes CHECK (estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 1440),
    CONSTRAINT chk_items_quality CHECK (quality_score BETWEEN 0 AND 1),
    CONSTRAINT chk_items_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT fk_items_category FOREIGN KEY (category_id)
        REFERENCES categories (category_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_preferences (
    user_id BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    preference_weight DECIMAL(5, 4) NOT NULL DEFAULT 1.0000,
    preference_source VARCHAR(20) NOT NULL DEFAULT 'USER',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, category_id),
    INDEX ix_user_preferences_category (category_id, user_id),
    CONSTRAINT chk_user_preferences_weight CHECK (preference_weight BETWEEN 0 AND 1),
    CONSTRAINT chk_user_preferences_source CHECK (preference_source IN ('USER', 'ONBOARDING')),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_user_preferences_category FOREIGN KEY (category_id)
        REFERENCES categories (category_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_interests (
    user_id BIGINT UNSIGNED NOT NULL,
    interest_id BIGINT UNSIGNED NOT NULL,
    interest_weight DECIMAL(5, 4) NOT NULL DEFAULT 1.0000,
    PRIMARY KEY (user_id, interest_id),
    INDEX ix_user_interests_interest (interest_id, user_id),
    CONSTRAINT chk_user_interests_weight CHECK (interest_weight BETWEEN 0 AND 1),
    CONSTRAINT fk_user_interests_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_user_interests_interest FOREIGN KEY (interest_id)
        REFERENCES interests (interest_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS item_interests (
    item_id BIGINT UNSIGNED NOT NULL,
    interest_id BIGINT UNSIGNED NOT NULL,
    relevance_weight DECIMAL(5, 4) NOT NULL DEFAULT 1.0000,
    PRIMARY KEY (item_id, interest_id),
    INDEX ix_item_interests_interest (interest_id, item_id),
    CONSTRAINT chk_item_interests_weight CHECK (relevance_weight BETWEEN 0 AND 1),
    CONSTRAINT fk_item_interests_item FOREIGN KEY (item_id)
        REFERENCES items (item_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_item_interests_interest FOREIGN KEY (interest_id)
        REFERENCES interests (interest_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS interest_relations (
    interest_id BIGINT UNSIGNED NOT NULL,
    related_interest_id BIGINT UNSIGNED NOT NULL,
    relevance_weight DECIMAL(5, 4) NOT NULL DEFAULT 0.5000,
    PRIMARY KEY (interest_id, related_interest_id),
    INDEX ix_interest_relations_related (related_interest_id, interest_id),
    CONSTRAINT chk_interest_relations_weight CHECK (relevance_weight BETWEEN 0 AND 1),
    CONSTRAINT fk_interest_relations_interest FOREIGN KEY (interest_id)
        REFERENCES interests (interest_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_interest_relations_related FOREIGN KEY (related_interest_id)
        REFERENCES interests (interest_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_onboarding (
    user_id BIGINT UNSIGNED NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT chk_user_onboarding_purpose CHECK (purpose IN ('LEARN', 'CAREER', 'EXPLORE', 'IMPROVE_MYSELF')),
    CONSTRAINT fk_user_onboarding_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_user_onboarding_category FOREIGN KEY (category_id)
        REFERENCES categories (category_id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_onboarding_topics (
    user_id BIGINT UNSIGNED NOT NULL,
    interest_id BIGINT UNSIGNED NOT NULL,
    selected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, interest_id),
    INDEX ix_onboarding_topics_interest (interest_id, user_id),
    CONSTRAINT fk_onboarding_topics_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_onboarding_topics_interest FOREIGN KEY (interest_id)
        REFERENCES interests (interest_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_topic_preferences (
    user_id BIGINT UNSIGNED NOT NULL,
    interest_id BIGINT UNSIGNED NOT NULL,
    positive_score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    negative_score DECIMAL(10, 4) NOT NULL DEFAULT 0,
    preference_score DECIMAL(6, 4) NOT NULL DEFAULT 0,
    interaction_count INT UNSIGNED NOT NULL DEFAULT 0,
    confidence DECIMAL(5, 4) NOT NULL DEFAULT 0,
    trend VARCHAR(10) NOT NULL DEFAULT 'STABLE',
    last_interacted_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, interest_id),
    INDEX ix_topic_preferences_interest_score (interest_id, preference_score),
    INDEX ix_topic_preferences_user_updated (user_id, updated_at),
    CONSTRAINT chk_topic_preferences_positive CHECK (positive_score >= 0),
    CONSTRAINT chk_topic_preferences_negative CHECK (negative_score >= 0),
    CONSTRAINT chk_topic_preferences_score CHECK (preference_score BETWEEN -1 AND 1),
    CONSTRAINT chk_topic_preferences_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_topic_preferences_trend CHECK (trend IN ('UP', 'DOWN', 'STABLE')),
    CONSTRAINT fk_topic_preferences_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_topic_preferences_interest FOREIGN KEY (interest_id)
        REFERENCES interests (interest_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS interactions (
    interaction_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NULL,
    interaction_type VARCHAR(30) NOT NULL,
    search_query VARCHAR(120) NULL,
    request_id CHAR(36) NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (interaction_id),
    INDEX ix_interactions_user_time (user_id, occurred_at),
    INDEX ix_interactions_type_recent_item_user (interaction_type, occurred_at, item_id, user_id),
    INDEX ix_interactions_user_type_recent_item (user_id, interaction_type, occurred_at, item_id),
    INDEX ix_interactions_item_type (item_id, interaction_type),
    UNIQUE INDEX uk_interactions_user_request (user_id, request_id),
    INDEX ix_interactions_user_item_progress (user_id, item_id, occurred_at, interaction_id),
    CONSTRAINT chk_interactions_type CHECK (
        interaction_type IN ('VIEW', 'CLICK', 'READ_PROGRESS', 'SAVE', 'PURCHASE', 'LIKE', 'DISLIKE', 'HIDE',
                             'SHARE', 'SKIP', 'MORE_LIKE', 'SEARCH', 'COMPLETE')
    ),
    CONSTRAINT chk_interactions_search_query CHECK (
        (interaction_type = 'SEARCH' AND search_query IS NOT NULL
            AND CHAR_LENGTH(TRIM(search_query)) BETWEEN 2 AND 120)
        OR (interaction_type <> 'SEARCH' AND search_query IS NULL)
    ),
    CONSTRAINT fk_interactions_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_interactions_item FOREIGN KEY (item_id)
        REFERENCES items (item_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS feedback (
    feedback_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    comment VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (feedback_id),
    CONSTRAINT uk_feedback_user_item UNIQUE (user_id, item_id),
    INDEX ix_feedback_item_rating (item_id, rating),
    CONSTRAINT chk_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_feedback_item FOREIGN KEY (item_id)
        REFERENCES items (item_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS recommendation_batches (
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

CREATE TABLE IF NOT EXISTS recommendations (
    recommendation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id BIGINT UNSIGNED NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    score DECIMAL(8, 4) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    reason_type VARCHAR(30) NOT NULL DEFAULT 'PROFILE_MATCH',
    bucket_type VARCHAR(20) NOT NULL DEFAULT 'FAMILIAR',
    algorithm_version VARCHAR(40) NOT NULL DEFAULT 'content-score-v1',
    explanation_json JSON NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recommendation_id),
    INDEX ix_recommendations_user_time (user_id, generated_at),
    INDEX ix_recommendations_item (item_id),
    INDEX ix_recommendations_algorithm_time (algorithm_version, generated_at),
    UNIQUE INDEX uk_recommendations_batch_item (batch_id, item_id),
    CONSTRAINT chk_recommendations_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT chk_recommendations_reason_type CHECK (
        reason_type IN ('ONBOARDING', 'PROFILE_MATCH', 'RECENT_ACTIVITY', 'RELATED_INTEREST', 'DISCOVERY',
                        'COLD_START', 'POPULARITY', 'COLLABORATIVE', 'RATING_AFFINITY', 'CONTENT_SIMILARITY')
    ),
    CONSTRAINT chk_recommendations_bucket_type CHECK (bucket_type IN ('FAMILIAR', 'RELATED', 'DISCOVERY')),
    CONSTRAINT fk_recommendations_batch_user FOREIGN KEY (batch_id, user_id)
        REFERENCES recommendation_batches (batch_id, user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_recommendations_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_recommendations_item FOREIGN KEY (item_id)
        REFERENCES items (item_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS recommendation_impressions (
    recommendation_id BIGINT UNSIGNED NOT NULL,
    shown_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    clicked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (recommendation_id),
    INDEX ix_recommendation_impressions_shown (shown_at, recommendation_id),
    INDEX ix_recommendation_impressions_clicked (clicked_at, recommendation_id),
    CONSTRAINT fk_recommendation_impressions_recommendation FOREIGN KEY (recommendation_id)
        REFERENCES recommendations (recommendation_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- Phase 3 reference additions (runtime uses append-only Flyway V14/V15).

-- V14__workspace_integrity.sql
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

-- V15__persistent_learning_journeys.sql
CREATE TABLE learning_journeys (
    journey_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    goal VARCHAR(30) NOT NULL,
    category_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    preference_snapshot JSON NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_journey_request(user_id,request_id),
    INDEX ix_journey_owner_time(user_id,created_at,journey_id),
    CONSTRAINT fk_journey_user FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_journey_category FOREIGN KEY(category_id) REFERENCES categories(category_id) ON DELETE SET NULL,
    CONSTRAINT chk_journey_goal CHECK(goal IN ('LEARN','CAREER','EXPLORE','IMPROVE_MYSELF')),
    CONSTRAINT chk_journey_status CHECK(status IN ('ACTIVE','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_journey_steps (
    step_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    journey_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NULL,
    position INT NOT NULL,
    title_snapshot VARCHAR(150) NOT NULL,
    rationale TEXT NOT NULL,
    UNIQUE KEY uk_journey_position(journey_id,position),
    UNIQUE KEY uk_journey_item(journey_id,item_id),
    CONSTRAINT fk_step_journey FOREIGN KEY(journey_id) REFERENCES learning_journeys(journey_id) ON DELETE CASCADE,
    CONSTRAINT fk_step_resource FOREIGN KEY(item_id) REFERENCES items(item_id) ON DELETE SET NULL,
    CONSTRAINT chk_step_position CHECK(position BETWEEN 1 AND 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE journey_step_replacements (
    replacement_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    step_id BIGINT UNSIGNED NOT NULL,
    previous_item_id BIGINT UNSIGNED NULL,
    previous_title VARCHAR(150) NOT NULL,
    previous_rationale TEXT NOT NULL,
    replaced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_replacement_step FOREIGN KEY(step_id) REFERENCES learning_journey_steps(step_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Phase 3.5 reference: V16 adds UNKNOWN difficulty, reviewed fine topics, catalog_sources,
-- catalog_import_batches, resource_provenance and catalog_import_reviews.
-- See the append-only V16__verified_catalog_ingestion.sql for authoritative DDL; this file is not an initializer.
