-- Persist admin-controlled familiar/related/discovery balance and retain an audit trail.
CREATE TABLE recommendation_system_settings (
    setting_key VARCHAR(40) NOT NULL,
    setting_value DECIMAL(6, 4) NOT NULL,
    updated_by BIGINT UNSIGNED NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key),
    CONSTRAINT chk_recommendation_setting_value CHECK (setting_value BETWEEN 0 AND 1),
    CONSTRAINT fk_recommendation_settings_user FOREIGN KEY (updated_by)
        REFERENCES users (user_id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_settings_audit (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(40) NOT NULL,
    old_value DECIMAL(6, 4) NOT NULL,
    new_value DECIMAL(6, 4) NOT NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    INDEX ix_recommendation_settings_audit_time (changed_at, audit_id),
    CONSTRAINT fk_recommendation_settings_audit_user FOREIGN KEY (actor_user_id)
        REFERENCES users (user_id) ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO recommendation_system_settings (setting_key, setting_value) VALUES
    ('familiar', 0.7000), ('related', 0.2000), ('discovery', 0.1000);
