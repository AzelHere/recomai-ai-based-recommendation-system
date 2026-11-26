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
