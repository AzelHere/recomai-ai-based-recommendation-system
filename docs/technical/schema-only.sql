-- Schema only. No records, credentials or user data.
CREATE TABLE `catalog_import_batches` (
  `batch_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `dataset_sha256` char(64) NOT NULL,
  `imported_count` int NOT NULL,
  `skipped_count` int NOT NULL,
  `review_count` int NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_catalog_batch_digest` (`dataset_sha256`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `catalog_import_reviews` (
  `source_id` varchar(40) NOT NULL,
  `external_id` varchar(160) NOT NULL,
  `title` varchar(150) NOT NULL,
  `canonical_url` varchar(2048) NOT NULL,
  `reason` varchar(255) NOT NULL,
  `last_seen_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`source_id`,`external_id`),
  CONSTRAINT `fk_import_review_source` FOREIGN KEY (`source_id`) REFERENCES `catalog_sources` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `catalog_sources` (
  `source_id` varchar(40) NOT NULL,
  `name` varchar(160) NOT NULL,
  `official_url` varchar(2048) NOT NULL,
  `license` varchar(80) NOT NULL,
  `license_url` varchar(2048) NOT NULL,
  `attribution_requirement` varchar(500) NOT NULL,
  `last_import_at` timestamp NULL DEFAULT NULL,
  `last_check_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `categories` (
  `category_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `niche_code` varchar(40) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_categories_name` (`name`),
  UNIQUE KEY `uk_categories_niche_code` (`niche_code`),
  KEY `ix_categories_active_name` (`is_active`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `feedback` (
  `feedback_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `item_id` bigint unsigned NOT NULL,
  `rating` tinyint unsigned NOT NULL,
  `comment` varchar(1000) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`feedback_id`),
  UNIQUE KEY `uk_feedback_user_item` (`user_id`,`item_id`),
  KEY `ix_feedback_item_rating` (`item_id`,`rating`),
  CONSTRAINT `fk_feedback_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_feedback_rating` CHECK ((`rating` between 1 and 5))
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interactions` (
  `interaction_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `item_id` bigint unsigned DEFAULT NULL,
  `interaction_type` varchar(30) NOT NULL,
  `search_query` varchar(120) DEFAULT NULL,
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `request_id` char(36) DEFAULT NULL,
  PRIMARY KEY (`interaction_id`),
  UNIQUE KEY `uk_interactions_user_request` (`user_id`,`request_id`),
  KEY `ix_interactions_user_time` (`user_id`,`occurred_at`),
  KEY `ix_interactions_item_type` (`item_id`,`interaction_type`),
  KEY `ix_interactions_type_recent_item_user` (`interaction_type`,`occurred_at`,`item_id`,`user_id`),
  KEY `ix_interactions_user_type_recent_item` (`user_id`,`interaction_type`,`occurred_at`,`item_id`),
  KEY `ix_interactions_user_item_progress` (`user_id`,`item_id`,`occurred_at`,`interaction_id`),
  KEY `ix_interactions_user_order` (`user_id`,`occurred_at` DESC,`interaction_id` DESC),
  KEY `ix_interactions_user_item_type` (`user_id`,`item_id`,`interaction_type`),
  CONSTRAINT `fk_interactions_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_interactions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_interactions_search_query` CHECK ((((`interaction_type` = _utf8mb4'SEARCH') and (`search_query` is not null) and (char_length(trim(`search_query`)) between 2 and 120)) or ((`interaction_type` <> _utf8mb4'SEARCH') and (`search_query` is null)))),
  CONSTRAINT `chk_interactions_type` CHECK ((`interaction_type` in (_utf8mb4'VIEW',_utf8mb4'CLICK',_utf8mb4'READ_PROGRESS',_utf8mb4'SAVE',_utf8mb4'PURCHASE',_utf8mb4'LIKE',_utf8mb4'DISLIKE',_utf8mb4'HIDE',_utf8mb4'SHARE',_utf8mb4'SKIP',_utf8mb4'MORE_LIKE',_utf8mb4'SEARCH',_utf8mb4'COMPLETE')))
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interest_relations` (
  `interest_id` bigint unsigned NOT NULL,
  `related_interest_id` bigint unsigned NOT NULL,
  `relevance_weight` decimal(5,4) NOT NULL DEFAULT '0.5000',
  PRIMARY KEY (`interest_id`,`related_interest_id`),
  KEY `ix_interest_relations_related` (`related_interest_id`,`interest_id`),
  CONSTRAINT `fk_interest_relations_interest` FOREIGN KEY (`interest_id`) REFERENCES `interests` (`interest_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_interest_relations_related` FOREIGN KEY (`related_interest_id`) REFERENCES `interests` (`interest_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_interest_relations_weight` CHECK ((`relevance_weight` between 0 and 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `interests` (
  `interest_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `category_id` bigint unsigned DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`interest_id`),
  UNIQUE KEY `uk_interests_name` (`name`),
  KEY `ix_interests_active_name` (`is_active`,`name`),
  KEY `ix_interests_category_active` (`category_id`,`is_active`,`name`),
  CONSTRAINT `fk_interests_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`category_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `item_interests` (
  `item_id` bigint unsigned NOT NULL,
  `interest_id` bigint unsigned NOT NULL,
  `relevance_weight` decimal(5,4) NOT NULL DEFAULT '1.0000',
  PRIMARY KEY (`item_id`,`interest_id`),
  KEY `ix_item_interests_interest` (`interest_id`,`item_id`),
  CONSTRAINT `fk_item_interests_interest` FOREIGN KEY (`interest_id`) REFERENCES `interests` (`interest_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_item_interests_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_item_interests_weight` CHECK ((`relevance_weight` between 0 and 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `items` (
  `item_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `category_id` bigint unsigned NOT NULL,
  `title` varchar(150) NOT NULL,
  `description` text,
  `item_type` varchar(20) NOT NULL DEFAULT 'CONTENT',
  `external_url` varchar(2048) DEFAULT NULL,
  `source_name` varchar(160) DEFAULT NULL,
  `difficulty` varchar(20) NOT NULL DEFAULT 'ALL_LEVELS',
  `estimated_minutes` smallint unsigned DEFAULT NULL,
  `quality_score` decimal(5,4) NOT NULL DEFAULT '0.5000',
  `is_editor_pick` tinyint(1) NOT NULL DEFAULT '0',
  `price` decimal(10,2) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `ix_items_category_active` (`category_id`,`is_active`),
  KEY `ix_items_type_active` (`item_type`,`is_active`),
  KEY `ix_items_discovery` (`category_id`,`is_active`,`quality_score`),
  KEY `ix_items_editor_pick_active` (`is_editor_pick`,`is_active`,`category_id`),
  CONSTRAINT `fk_items_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`category_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_items_difficulty` CHECK ((`difficulty` in (_utf8mb4'BEGINNER',_utf8mb4'INTERMEDIATE',_utf8mb4'ADVANCED',_utf8mb4'ALL_LEVELS',_utf8mb4'UNKNOWN'))),
  CONSTRAINT `chk_items_estimated_minutes` CHECK (((`estimated_minutes` is null) or (`estimated_minutes` between 1 and 1440))),
  CONSTRAINT `chk_items_price` CHECK (((`price` is null) or (`price` >= 0))),
  CONSTRAINT `chk_items_quality` CHECK ((`quality_score` between 0 and 1)),
  CONSTRAINT `chk_items_type` CHECK ((`item_type` in (_utf8mb4'PRODUCT',_utf8mb4'CONTENT',_utf8mb4'COURSE',_utf8mb4'RESOURCE',_utf8mb4'ARTICLE',_utf8mb4'TOOL',_utf8mb4'PROJECT',_utf8mb4'OTHER',_utf8mb4'VIDEO',_utf8mb4'DOCUMENTATION',_utf8mb4'GUIDE')))
) ENGINE=InnoDB AUTO_INCREMENT=1868 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `journey_step_replacements` (
  `replacement_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `step_id` bigint unsigned NOT NULL,
  `previous_item_id` bigint unsigned DEFAULT NULL,
  `previous_title` varchar(150) NOT NULL,
  `previous_rationale` text NOT NULL,
  `replaced_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`replacement_id`),
  KEY `fk_replacement_step` (`step_id`),
  CONSTRAINT `fk_replacement_step` FOREIGN KEY (`step_id`) REFERENCES `learning_journey_steps` (`step_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learning_journey_steps` (
  `step_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `journey_id` bigint unsigned NOT NULL,
  `item_id` bigint unsigned DEFAULT NULL,
  `position` int NOT NULL,
  `title_snapshot` varchar(150) NOT NULL,
  `rationale` text NOT NULL,
  PRIMARY KEY (`step_id`),
  UNIQUE KEY `uk_journey_position` (`journey_id`,`position`),
  UNIQUE KEY `uk_journey_item` (`journey_id`,`item_id`),
  KEY `fk_step_resource` (`item_id`),
  CONSTRAINT `fk_step_journey` FOREIGN KEY (`journey_id`) REFERENCES `learning_journeys` (`journey_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_step_resource` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE SET NULL,
  CONSTRAINT `chk_step_position` CHECK ((`position` between 1 and 12))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `learning_journeys` (
  `journey_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `title` varchar(150) NOT NULL,
  `goal` varchar(30) NOT NULL,
  `category_id` bigint unsigned DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE',
  `preference_snapshot` json NOT NULL,
  `request_id` varchar(64) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`journey_id`),
  UNIQUE KEY `uk_journey_request` (`user_id`,`request_id`),
  KEY `ix_journey_owner_time` (`user_id`,`created_at`,`journey_id`),
  KEY `fk_journey_category` (`category_id`),
  CONSTRAINT `fk_journey_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`category_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_journey_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `chk_journey_goal` CHECK ((`goal` in (_utf8mb4'LEARN',_utf8mb4'CAREER',_utf8mb4'EXPLORE',_utf8mb4'IMPROVE_MYSELF'))),
  CONSTRAINT `chk_journey_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'ARCHIVED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `password_reset_tokens` (
  `reset_token_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `token_hash` binary(32) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `expires_at` timestamp(6) NOT NULL,
  `consumed_at` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`reset_token_id`),
  UNIQUE KEY `uk_password_reset_token_hash` (`token_hash`),
  KEY `ix_password_reset_user_created` (`user_id`,`created_at`),
  KEY `ix_password_reset_expiry` (`expires_at`),
  CONSTRAINT `fk_password_reset_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation_batches` (
  `batch_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `algorithm_version` varchar(40) NOT NULL DEFAULT 'content-score-v1',
  `generated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_recommendation_batches_batch_user` (`batch_id`,`user_id`),
  KEY `ix_recommendation_batches_user_time` (`user_id`,`generated_at`,`batch_id`),
  CONSTRAINT `fk_recommendation_batches_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation_impressions` (
  `recommendation_id` bigint unsigned NOT NULL,
  `shown_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `clicked_at` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`recommendation_id`),
  KEY `ix_recommendation_impressions_shown` (`shown_at`,`recommendation_id`),
  KEY `ix_recommendation_impressions_clicked` (`clicked_at`,`recommendation_id`),
  CONSTRAINT `fk_recommendation_impressions_recommendation` FOREIGN KEY (`recommendation_id`) REFERENCES `recommendations` (`recommendation_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation_settings_audit` (
  `audit_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `setting_key` varchar(40) NOT NULL,
  `old_value` decimal(6,4) NOT NULL,
  `new_value` decimal(6,4) NOT NULL,
  `actor_user_id` bigint unsigned DEFAULT NULL,
  `changed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`audit_id`),
  KEY `ix_recommendation_settings_audit_time` (`changed_at`,`audit_id`),
  KEY `fk_recommendation_settings_audit_user` (`actor_user_id`),
  CONSTRAINT `fk_recommendation_settings_audit_user` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation_settings_revision` (
  `singleton_id` int NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`singleton_id`),
  CONSTRAINT `chk_settings_singleton` CHECK ((`singleton_id` = 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation_system_settings` (
  `setting_key` varchar(40) NOT NULL,
  `setting_value` decimal(6,4) NOT NULL,
  `updated_by` bigint unsigned DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`setting_key`),
  KEY `fk_recommendation_settings_user` (`updated_by`),
  CONSTRAINT `fk_recommendation_settings_user` FOREIGN KEY (`updated_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_recommendation_setting_value` CHECK ((`setting_value` between 0 and 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recommendations` (
  `recommendation_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `batch_id` bigint unsigned DEFAULT NULL,
  `user_id` bigint unsigned NOT NULL,
  `item_id` bigint unsigned NOT NULL,
  `score` decimal(8,4) NOT NULL,
  `reason` varchar(1000) NOT NULL,
  `reason_type` varchar(30) NOT NULL DEFAULT 'PROFILE_MATCH',
  `bucket_type` varchar(20) NOT NULL DEFAULT 'FAMILIAR',
  `algorithm_version` varchar(40) NOT NULL DEFAULT 'content-score-v1',
  `explanation_json` json DEFAULT NULL,
  `generated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`recommendation_id`),
  UNIQUE KEY `uk_recommendations_batch_item` (`batch_id`,`item_id`),
  KEY `ix_recommendations_user_time` (`user_id`,`generated_at`),
  KEY `ix_recommendations_item` (`item_id`),
  KEY `fk_recommendations_batch_user` (`batch_id`,`user_id`),
  KEY `ix_recommendations_algorithm_time` (`algorithm_version`,`generated_at`),
  CONSTRAINT `fk_recommendations_batch_user` FOREIGN KEY (`batch_id`, `user_id`) REFERENCES `recommendation_batches` (`batch_id`, `user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_recommendations_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_recommendations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_recommendations_bucket_type` CHECK ((`bucket_type` in (_utf8mb4'FAMILIAR',_utf8mb4'RELATED',_utf8mb4'DISCOVERY'))),
  CONSTRAINT `chk_recommendations_reason_type` CHECK ((`reason_type` in (_utf8mb4'ONBOARDING',_utf8mb4'PROFILE_MATCH',_utf8mb4'RECENT_ACTIVITY',_utf8mb4'RELATED_INTEREST',_utf8mb4'DISCOVERY',_utf8mb4'COLD_START',_utf8mb4'POPULARITY',_utf8mb4'COLLABORATIVE',_utf8mb4'RATING_AFFINITY',_utf8mb4'CONTENT_SIMILARITY',_utf8mb4'EDITOR_PICK'))),
  CONSTRAINT `chk_recommendations_score` CHECK ((`score` between 0 and 100))
) ENGINE=InnoDB AUTO_INCREMENT=566 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `resource_provenance` (
  `item_id` bigint unsigned NOT NULL,
  `source_id` varchar(40) NOT NULL,
  `external_id` varchar(160) NOT NULL,
  `canonical_url` varchar(2048) NOT NULL,
  `canonical_sha256` char(64) NOT NULL,
  `language` varchar(16) DEFAULT NULL,
  `license` varchar(80) NOT NULL,
  `attribution` text NOT NULL,
  `metadata_origin` text NOT NULL,
  `source_topics` json NOT NULL,
  `batch_id` bigint unsigned NOT NULL,
  `acquired_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `url_status` varchar(32) NOT NULL DEFAULT 'NOT_CHECKED',
  `url_checked_at` timestamp NULL DEFAULT NULL,
  `final_url` varchar(2048) DEFAULT NULL,
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uk_provenance_source_external` (`source_id`,`external_id`),
  UNIQUE KEY `uk_provenance_canonical` (`canonical_sha256`),
  KEY `ix_provenance_url_check` (`url_checked_at`,`item_id`),
  KEY `fk_provenance_batch` (`batch_id`),
  CONSTRAINT `fk_provenance_batch` FOREIGN KEY (`batch_id`) REFERENCES `catalog_import_batches` (`batch_id`),
  CONSTRAINT `fk_provenance_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_provenance_source` FOREIGN KEY (`source_id`) REFERENCES `catalog_sources` (`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_collection_items` (
  `collection_id` bigint unsigned NOT NULL,
  `item_id` bigint unsigned NOT NULL,
  `added_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`collection_id`,`item_id`),
  KEY `ix_user_collection_items_item` (`item_id`),
  CONSTRAINT `fk_user_collection_items_collection` FOREIGN KEY (`collection_id`) REFERENCES `user_collections` (`collection_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_collection_items_item` FOREIGN KEY (`item_id`) REFERENCES `items` (`item_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_collections` (
  `collection_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `name` varchar(80) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`collection_id`),
  UNIQUE KEY `uk_user_collections_name` (`user_id`,`name`),
  KEY `ix_user_collections_user_time` (`user_id`,`updated_at`),
  CONSTRAINT `fk_user_collections_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_interests` (
  `user_id` bigint unsigned NOT NULL,
  `interest_id` bigint unsigned NOT NULL,
  `interest_weight` decimal(5,4) NOT NULL DEFAULT '1.0000',
  PRIMARY KEY (`user_id`,`interest_id`),
  KEY `ix_user_interests_interest` (`interest_id`,`user_id`),
  CONSTRAINT `fk_user_interests_interest` FOREIGN KEY (`interest_id`) REFERENCES `interests` (`interest_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_interests_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_user_interests_weight` CHECK ((`interest_weight` between 0 and 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_onboarding` (
  `user_id` bigint unsigned NOT NULL,
  `purpose` varchar(30) NOT NULL,
  `category_id` bigint unsigned NOT NULL,
  `completed_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  KEY `fk_user_onboarding_category` (`category_id`),
  CONSTRAINT `fk_user_onboarding_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`category_id`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_user_onboarding_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_user_onboarding_purpose` CHECK ((`purpose` in (_utf8mb4'LEARN',_utf8mb4'CAREER',_utf8mb4'EXPLORE',_utf8mb4'IMPROVE_MYSELF')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_onboarding_topics` (
  `user_id` bigint unsigned NOT NULL,
  `interest_id` bigint unsigned NOT NULL,
  `selected_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`interest_id`),
  KEY `ix_onboarding_topics_interest` (`interest_id`,`user_id`),
  CONSTRAINT `fk_onboarding_topics_interest` FOREIGN KEY (`interest_id`) REFERENCES `interests` (`interest_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_onboarding_topics_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_preferences` (
  `user_id` bigint unsigned NOT NULL,
  `category_id` bigint unsigned NOT NULL,
  `preference_weight` decimal(5,4) NOT NULL DEFAULT '1.0000',
  `preference_source` varchar(20) NOT NULL DEFAULT 'USER',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`category_id`),
  KEY `ix_user_preferences_category` (`category_id`,`user_id`),
  CONSTRAINT `fk_user_preferences_category` FOREIGN KEY (`category_id`) REFERENCES `categories` (`category_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_preferences_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_user_preferences_source` CHECK ((`preference_source` in (_utf8mb4'USER',_utf8mb4'ONBOARDING'))),
  CONSTRAINT `chk_user_preferences_weight` CHECK ((`preference_weight` between 0 and 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_topic_preferences` (
  `user_id` bigint unsigned NOT NULL,
  `interest_id` bigint unsigned NOT NULL,
  `positive_score` decimal(10,4) NOT NULL DEFAULT '0.0000',
  `negative_score` decimal(10,4) NOT NULL DEFAULT '0.0000',
  `preference_score` decimal(6,4) NOT NULL DEFAULT '0.0000',
  `interaction_count` int unsigned NOT NULL DEFAULT '0',
  `confidence` decimal(5,4) NOT NULL DEFAULT '0.0000',
  `trend` varchar(10) NOT NULL DEFAULT 'STABLE',
  `last_interacted_at` timestamp NULL DEFAULT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`,`interest_id`),
  KEY `ix_topic_preferences_interest_score` (`interest_id`,`preference_score`),
  KEY `ix_topic_preferences_user_updated` (`user_id`,`updated_at`),
  CONSTRAINT `fk_topic_preferences_interest` FOREIGN KEY (`interest_id`) REFERENCES `interests` (`interest_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_topic_preferences_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `chk_topic_preferences_confidence` CHECK ((`confidence` between 0 and 1)),
  CONSTRAINT `chk_topic_preferences_negative` CHECK ((`negative_score` >= 0)),
  CONSTRAINT `chk_topic_preferences_positive` CHECK ((`positive_score` >= 0)),
  CONSTRAINT `chk_topic_preferences_score` CHECK ((`preference_score` between -(1) and 1)),
  CONSTRAINT `chk_topic_preferences_trend` CHECK ((`trend` in (_utf8mb4'UP',_utf8mb4'DOWN',_utf8mb4'STABLE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `user_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `email` varchar(254) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `display_name` varchar(100) NOT NULL,
  `role` varchar(20) NOT NULL DEFAULT 'USER',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `auth_version` int unsigned NOT NULL DEFAULT '1',
  `exploration` tinyint unsigned NOT NULL DEFAULT '50',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`),
  CONSTRAINT `chk_users_exploration` CHECK ((`exploration` between 0 and 100)),
  CONSTRAINT `chk_users_role` CHECK ((`role` in (_utf8mb4'USER',_utf8mb4'ADMIN')))
) ENGINE=InnoDB AUTO_INCREMENT=83 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
