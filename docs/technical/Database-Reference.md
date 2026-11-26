# Database reference
The inspected MySQL 8.4 schema contains 30 tables including Flyway history and 29 business tables. V1 through V18 are successful. This schema-only reference contains no accounts, passwords, tokens or private records. Surrogate and composite keys separate identities from relationships; foreign keys enforce referential integrity. No blanket claim of a specific normal form is made. Denormalized journey snapshots intentionally preserve the context used when recommendations were generated.

`items` is the shared resource identity. `resource_provenance` adds one source record per imported resource, with unique source/external ID and canonical hash constraints. Sources, import batches and review rows preserve acquisition decisions separately from accepted catalog items. Owner tables refer to users and resources; collections and journey steps use explicit membership/order constraints. ON DELETE behavior below must be understood before administrative deletion or account removal. Index names show implemented indexes; they do not prove every LIKE/LOWER query uses an index. Phase 4 EXPLAIN evidence records actual plans.

Column table fields are MySQL SHOW FULL COLUMNS output: type, collation, nullability, key hint, default, extra attributes, privileges and comment. Index rows below include actual column order and uniqueness. The companion schema-only.sql is executable DDL for inspection, not the deployment migration entry point. Fresh deployment uses Flyway and the catalog loader, not this snapshot.

## catalog_import_batches

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| batch_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| dataset_sha256 | char(64) | NO | UNI | NULL |  |
| imported_count | int | NO |  | NULL |  |
| skipped_count | int | NO |  | NULL |  |
| review_count | int | NO |  | NULL |  |
| created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

Foreign keys: none.

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | batch_id | BTREE |
| uk_catalog_batch_digest | True | 1 | dataset_sha256 | BTREE |

## catalog_import_reviews

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| source_id | varchar(40) | NO | PRI | NULL |  |
| external_id | varchar(160) | NO | PRI | NULL |  |
| title | varchar(150) | NO |  | NULL |  |
| canonical_url | varchar(2048) | NO |  | NULL |  |
| reason | varchar(255) | NO |  | NULL |  |
| last_seen_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

Foreign keys: source_id → catalog_sources.source_id (fk_import_review_source).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | source_id | BTREE |
| PRIMARY | True | 2 | external_id | BTREE |

## catalog_sources

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| source_id | varchar(40) | NO | PRI | NULL |  |
| name | varchar(160) | NO |  | NULL |  |
| official_url | varchar(2048) | NO |  | NULL |  |
| license | varchar(80) | NO |  | NULL |  |
| license_url | varchar(2048) | NO |  | NULL |  |
| attribution_requirement | varchar(500) | NO |  | NULL |  |
| last_import_at | timestamp | YES |  | NULL |  |
| last_check_at | timestamp | YES |  | NULL |  |

Foreign keys: none.

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | source_id | BTREE |

## categories

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| category_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| name | varchar(100) | NO | UNI | NULL |  |
| niche_code | varchar(40) | YES | UNI | NULL |  |
| description | varchar(500) | YES |  | NULL |  |
| is_active | tinyint(1) | NO | MUL | 1 |  |
| created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: none.

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | category_id | BTREE |
| uk_categories_name | True | 1 | name | BTREE |
| uk_categories_niche_code | True | 1 | niche_code | BTREE |
| ix_categories_active_name | False | 1 | is_active | BTREE |
| ix_categories_active_name | False | 2 | name | BTREE |

## feedback

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| feedback_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| user_id | bigint unsigned | NO | MUL | NULL |  |
| item_id | bigint unsigned | NO | MUL | NULL |  |
| rating | tinyint unsigned | NO |  | NULL |  |
| comment | varchar(1000) | YES |  | NULL |  |
| created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: item_id → items.item_id (fk_feedback_item); user_id → users.user_id (fk_feedback_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | feedback_id | BTREE |
| uk_feedback_user_item | True | 1 | user_id | BTREE |
| uk_feedback_user_item | True | 2 | item_id | BTREE |
| ix_feedback_item_rating | False | 1 | item_id | BTREE |
| ix_feedback_item_rating | False | 2 | rating | BTREE |

## flyway_schema_history

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| installed_rank | int | NO | PRI | NULL |  |
| version | varchar(50) | YES |  | NULL |  |
| description | varchar(200) | NO |  | NULL |  |
| type | varchar(20) | NO |  | NULL |  |
| script | varchar(1000) | NO |  | NULL |  |
| checksum | int | YES |  | NULL |  |
| installed_by | varchar(100) | NO |  | NULL |  |
| installed_on | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| execution_time | int | NO |  | NULL |  |
| success | tinyint(1) | NO | MUL | NULL |  |

Foreign keys: none.

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | installed_rank | BTREE |
| flyway_schema_history_s_idx | False | 1 | success | BTREE |

## interactions

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| interaction_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| user_id | bigint unsigned | NO | MUL | NULL |  |
| item_id | bigint unsigned | YES | MUL | NULL |  |
| interaction_type | varchar(30) | NO | MUL | NULL |  |
| search_query | varchar(120) | YES |  | NULL |  |
| occurred_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| request_id | char(36) | YES |  | NULL |  |

Foreign keys: item_id → items.item_id (fk_interactions_item); user_id → users.user_id (fk_interactions_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | interaction_id | BTREE |
| uk_interactions_user_request | True | 1 | user_id | BTREE |
| uk_interactions_user_request | True | 2 | request_id | BTREE |
| ix_interactions_user_time | False | 1 | user_id | BTREE |
| ix_interactions_user_time | False | 2 | occurred_at | BTREE |
| ix_interactions_item_type | False | 1 | item_id | BTREE |
| ix_interactions_item_type | False | 2 | interaction_type | BTREE |
| ix_interactions_type_recent_item_user | False | 1 | interaction_type | BTREE |
| ix_interactions_type_recent_item_user | False | 2 | occurred_at | BTREE |
| ix_interactions_type_recent_item_user | False | 3 | item_id | BTREE |
| ix_interactions_type_recent_item_user | False | 4 | user_id | BTREE |
| ix_interactions_user_type_recent_item | False | 1 | user_id | BTREE |
| ix_interactions_user_type_recent_item | False | 2 | interaction_type | BTREE |
| ix_interactions_user_type_recent_item | False | 3 | occurred_at | BTREE |
| ix_interactions_user_type_recent_item | False | 4 | item_id | BTREE |
| ix_interactions_user_item_progress | False | 1 | user_id | BTREE |
| ix_interactions_user_item_progress | False | 2 | item_id | BTREE |
| ix_interactions_user_item_progress | False | 3 | occurred_at | BTREE |
| ix_interactions_user_item_progress | False | 4 | interaction_id | BTREE |
| ix_interactions_user_order | False | 1 | user_id | BTREE |
| ix_interactions_user_order | False | 2 | occurred_at | BTREE |
| ix_interactions_user_order | False | 3 | interaction_id | BTREE |
| ix_interactions_user_item_type | False | 1 | user_id | BTREE |
| ix_interactions_user_item_type | False | 2 | item_id | BTREE |
| ix_interactions_user_item_type | False | 3 | interaction_type | BTREE |

## interest_relations

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| interest_id | bigint unsigned | NO | PRI | NULL |  |
| related_interest_id | bigint unsigned | NO | PRI | NULL |  |
| relevance_weight | decimal(5,4) | NO |  | 0.5000 |  |

Foreign keys: interest_id → interests.interest_id (fk_interest_relations_interest); related_interest_id → interests.interest_id (fk_interest_relations_related).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | interest_id | BTREE |
| PRIMARY | True | 2 | related_interest_id | BTREE |
| ix_interest_relations_related | False | 1 | related_interest_id | BTREE |
| ix_interest_relations_related | False | 2 | interest_id | BTREE |

## interests

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| interest_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| category_id | bigint unsigned | YES | MUL | NULL |  |
| name | varchar(100) | NO | UNI | NULL |  |
| is_active | tinyint(1) | NO | MUL | 1 |  |
| created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: category_id → categories.category_id (fk_interests_category).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | interest_id | BTREE |
| uk_interests_name | True | 1 | name | BTREE |
| ix_interests_active_name | False | 1 | is_active | BTREE |
| ix_interests_active_name | False | 2 | name | BTREE |
| ix_interests_category_active | False | 1 | category_id | BTREE |
| ix_interests_category_active | False | 2 | is_active | BTREE |
| ix_interests_category_active | False | 3 | name | BTREE |

## item_interests

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| item_id | bigint unsigned | NO | PRI | NULL |  |
| interest_id | bigint unsigned | NO | PRI | NULL |  |
| relevance_weight | decimal(5,4) | NO |  | 1.0000 |  |

Foreign keys: interest_id → interests.interest_id (fk_item_interests_interest); item_id → items.item_id (fk_item_interests_item).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | item_id | BTREE |
| PRIMARY | True | 2 | interest_id | BTREE |
| ix_item_interests_interest | False | 1 | interest_id | BTREE |
| ix_item_interests_interest | False | 2 | item_id | BTREE |

## items

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| item_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| category_id | bigint unsigned | NO | MUL | NULL |  |
| title | varchar(150) | NO |  | NULL |  |
| description | text | YES |  | NULL |  |
| item_type | varchar(20) | NO | MUL | CONTENT |  |
| external_url | varchar(2048) | YES |  | NULL |  |
| source_name | varchar(160) | YES |  | NULL |  |
| difficulty | varchar(20) | NO |  | ALL_LEVELS |  |
| estimated_minutes | smallint unsigned | YES |  | NULL |  |
| quality_score | decimal(5,4) | NO |  | 0.5000 |  |
| is_editor_pick | tinyint(1) | NO | MUL | 0 |  |
| price | decimal(10,2) | YES |  | NULL |  |
| is_active | tinyint(1) | NO |  | 1 |  |
| created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |
| revision | bigint | NO |  | 0 |  |

Foreign keys: category_id → categories.category_id (fk_items_category).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | item_id | BTREE |
| ix_items_category_active | False | 1 | category_id | BTREE |
| ix_items_category_active | False | 2 | is_active | BTREE |
| ix_items_type_active | False | 1 | item_type | BTREE |
| ix_items_type_active | False | 2 | is_active | BTREE |
| ix_items_discovery | False | 1 | category_id | BTREE |
| ix_items_discovery | False | 2 | is_active | BTREE |
| ix_items_discovery | False | 3 | quality_score | BTREE |
| ix_items_editor_pick_active | False | 1 | is_editor_pick | BTREE |
| ix_items_editor_pick_active | False | 2 | is_active | BTREE |
| ix_items_editor_pick_active | False | 3 | category_id | BTREE |

## journey_step_replacements

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| replacement_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| step_id | bigint unsigned | NO | MUL | NULL |  |
| previous_item_id | bigint unsigned | YES |  | NULL |  |
| previous_title | varchar(150) | NO |  | NULL |  |
| previous_rationale | text | NO |  | NULL |  |
| replaced_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

Foreign keys: step_id → learning_journey_steps.step_id (fk_replacement_step).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | replacement_id | BTREE |
| fk_replacement_step | False | 1 | step_id | BTREE |

## learning_journey_steps

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| step_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| journey_id | bigint unsigned | NO | MUL | NULL |  |
| item_id | bigint unsigned | YES | MUL | NULL |  |
| position | int | NO |  | NULL |  |
| title_snapshot | varchar(150) | NO |  | NULL |  |
| rationale | text | NO |  | NULL |  |

Foreign keys: journey_id → learning_journeys.journey_id (fk_step_journey); item_id → items.item_id (fk_step_resource).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | step_id | BTREE |
| uk_journey_position | True | 1 | journey_id | BTREE |
| uk_journey_position | True | 2 | position | BTREE |
| uk_journey_item | True | 1 | journey_id | BTREE |
| uk_journey_item | True | 2 | item_id | BTREE |
| fk_step_resource | False | 1 | item_id | BTREE |

## learning_journeys

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| journey_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| user_id | bigint unsigned | NO | MUL | NULL |  |
| title | varchar(150) | NO |  | NULL |  |
| goal | varchar(30) | NO |  | NULL |  |
| category_id | bigint unsigned | YES | MUL | NULL |  |
| status | varchar(20) | NO |  | ACTIVE |  |
| preference_snapshot | json | NO |  | NULL |  |
| request_id | varchar(64) | NO |  | NULL |  |
| request_hash | char(64) | NO |  | NULL |  |
| created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: category_id → categories.category_id (fk_journey_category); user_id → users.user_id (fk_journey_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | journey_id | BTREE |
| uk_journey_request | True | 1 | user_id | BTREE |
| uk_journey_request | True | 2 | request_id | BTREE |
| ix_journey_owner_time | False | 1 | user_id | BTREE |
| ix_journey_owner_time | False | 2 | created_at | BTREE |
| ix_journey_owner_time | False | 3 | journey_id | BTREE |
| fk_journey_category | False | 1 | category_id | BTREE |

## password_reset_tokens

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| reset_token_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| user_id | bigint unsigned | NO | MUL | NULL |  |
| token_hash | binary(32) | NO | UNI | NULL |  |
| created_at | timestamp(6) | NO |  | CURRENT_TIMESTAMP(6) | DEFAULT_GENERATED |
| expires_at | timestamp(6) | NO | MUL | NULL |  |
| consumed_at | timestamp(6) | YES |  | NULL |  |

Foreign keys: user_id → users.user_id (fk_password_reset_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | reset_token_id | BTREE |
| uk_password_reset_token_hash | True | 1 | token_hash | BTREE |
| ix_password_reset_user_created | False | 1 | user_id | BTREE |
| ix_password_reset_user_created | False | 2 | created_at | BTREE |
| ix_password_reset_expiry | False | 1 | expires_at | BTREE |

## recommendation_batches

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| batch_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| user_id | bigint unsigned | NO | MUL | NULL |  |
| algorithm_version | varchar(40) | NO |  | content-score-v1 |  |
| generated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

Foreign keys: user_id → users.user_id (fk_recommendation_batches_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | batch_id | BTREE |
| uk_recommendation_batches_batch_user | True | 1 | batch_id | BTREE |
| uk_recommendation_batches_batch_user | True | 2 | user_id | BTREE |
| ix_recommendation_batches_user_time | False | 1 | user_id | BTREE |
| ix_recommendation_batches_user_time | False | 2 | generated_at | BTREE |
| ix_recommendation_batches_user_time | False | 3 | batch_id | BTREE |

## recommendation_impressions

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| recommendation_id | bigint unsigned | NO | PRI | NULL |  |
| shown_at | timestamp(6) | NO | MUL | CURRENT_TIMESTAMP(6) | DEFAULT_GENERATED |
| clicked_at | timestamp(6) | YES | MUL | NULL |  |

Foreign keys: recommendation_id → recommendations.recommendation_id (fk_recommendation_impressions_recommendation).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | recommendation_id | BTREE |
| ix_recommendation_impressions_shown | False | 1 | shown_at | BTREE |
| ix_recommendation_impressions_shown | False | 2 | recommendation_id | BTREE |
| ix_recommendation_impressions_clicked | False | 1 | clicked_at | BTREE |
| ix_recommendation_impressions_clicked | False | 2 | recommendation_id | BTREE |

## recommendation_settings_audit

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| audit_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| setting_key | varchar(40) | NO |  | NULL |  |
| old_value | decimal(6,4) | NO |  | NULL |  |
| new_value | decimal(6,4) | NO |  | NULL |  |
| actor_user_id | bigint unsigned | YES | MUL | NULL |  |
| changed_at | timestamp | NO | MUL | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

Foreign keys: actor_user_id → users.user_id (fk_recommendation_settings_audit_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | audit_id | BTREE |
| ix_recommendation_settings_audit_time | False | 1 | changed_at | BTREE |
| ix_recommendation_settings_audit_time | False | 2 | audit_id | BTREE |
| fk_recommendation_settings_audit_user | False | 1 | actor_user_id | BTREE |

## recommendation_settings_revision

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| singleton_id | int | NO | PRI | NULL |  |
| revision | bigint | NO |  | 0 |  |

Foreign keys: none.

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | singleton_id | BTREE |

## recommendation_system_settings

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| setting_key | varchar(40) | NO | PRI | NULL |  |
| setting_value | decimal(6,4) | NO |  | NULL |  |
| updated_by | bigint unsigned | YES | MUL | NULL |  |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: updated_by → users.user_id (fk_recommendation_settings_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | setting_key | BTREE |
| fk_recommendation_settings_user | False | 1 | updated_by | BTREE |

## recommendations

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| recommendation_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| batch_id | bigint unsigned | YES | MUL | NULL |  |
| user_id | bigint unsigned | NO | MUL | NULL |  |
| item_id | bigint unsigned | NO | MUL | NULL |  |
| score | decimal(8,4) | NO |  | NULL |  |
| reason | varchar(1000) | NO |  | NULL |  |
| reason_type | varchar(30) | NO |  | PROFILE_MATCH |  |
| bucket_type | varchar(20) | NO |  | FAMILIAR |  |
| algorithm_version | varchar(40) | NO | MUL | content-score-v1 |  |
| explanation_json | json | YES |  | NULL |  |
| generated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

Foreign keys: batch_id → recommendation_batches.batch_id (fk_recommendations_batch_user); user_id → recommendation_batches.user_id (fk_recommendations_batch_user); item_id → items.item_id (fk_recommendations_item); user_id → users.user_id (fk_recommendations_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | recommendation_id | BTREE |
| uk_recommendations_batch_item | True | 1 | batch_id | BTREE |
| uk_recommendations_batch_item | True | 2 | item_id | BTREE |
| ix_recommendations_user_time | False | 1 | user_id | BTREE |
| ix_recommendations_user_time | False | 2 | generated_at | BTREE |
| ix_recommendations_item | False | 1 | item_id | BTREE |
| fk_recommendations_batch_user | False | 1 | batch_id | BTREE |
| fk_recommendations_batch_user | False | 2 | user_id | BTREE |
| ix_recommendations_algorithm_time | False | 1 | algorithm_version | BTREE |
| ix_recommendations_algorithm_time | False | 2 | generated_at | BTREE |

## resource_provenance

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| item_id | bigint unsigned | NO | PRI | NULL |  |
| source_id | varchar(40) | NO | MUL | NULL |  |
| external_id | varchar(160) | NO |  | NULL |  |
| canonical_url | varchar(2048) | NO |  | NULL |  |
| canonical_sha256 | char(64) | NO | UNI | NULL |  |
| language | varchar(16) | YES |  | NULL |  |
| license | varchar(80) | NO |  | NULL |  |
| attribution | text | NO |  | NULL |  |
| metadata_origin | text | NO |  | NULL |  |
| source_topics | json | NO |  | NULL |  |
| batch_id | bigint unsigned | NO | MUL | NULL |  |
| acquired_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| url_status | varchar(32) | NO |  | NOT_CHECKED |  |
| url_checked_at | timestamp | YES | MUL | NULL |  |
| final_url | varchar(2048) | YES |  | NULL |  |

Foreign keys: batch_id → catalog_import_batches.batch_id (fk_provenance_batch); item_id → items.item_id (fk_provenance_item); source_id → catalog_sources.source_id (fk_provenance_source).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | item_id | BTREE |
| uk_provenance_source_external | True | 1 | source_id | BTREE |
| uk_provenance_source_external | True | 2 | external_id | BTREE |
| uk_provenance_canonical | True | 1 | canonical_sha256 | BTREE |
| ix_provenance_url_check | False | 1 | url_checked_at | BTREE |
| ix_provenance_url_check | False | 2 | item_id | BTREE |
| fk_provenance_batch | False | 1 | batch_id | BTREE |

## user_collection_items

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| collection_id | bigint unsigned | NO | PRI | NULL |  |
| item_id | bigint unsigned | NO | PRI | NULL |  |
| added_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

Foreign keys: collection_id → user_collections.collection_id (fk_user_collection_items_collection); item_id → items.item_id (fk_user_collection_items_item).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | collection_id | BTREE |
| PRIMARY | True | 2 | item_id | BTREE |
| ix_user_collection_items_item | False | 1 | item_id | BTREE |

## user_collections

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| collection_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| user_id | bigint unsigned | NO | MUL | NULL |  |
| name | varchar(80) | NO |  | NULL |  |
| created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: user_id → users.user_id (fk_user_collections_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | collection_id | BTREE |
| uk_user_collections_name | True | 1 | user_id | BTREE |
| uk_user_collections_name | True | 2 | name | BTREE |
| ix_user_collections_user_time | False | 1 | user_id | BTREE |
| ix_user_collections_user_time | False | 2 | updated_at | BTREE |

## user_interests

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| user_id | bigint unsigned | NO | PRI | NULL |  |
| interest_id | bigint unsigned | NO | PRI | NULL |  |
| interest_weight | decimal(5,4) | NO |  | 1.0000 |  |

Foreign keys: interest_id → interests.interest_id (fk_user_interests_interest); user_id → users.user_id (fk_user_interests_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | user_id | BTREE |
| PRIMARY | True | 2 | interest_id | BTREE |
| ix_user_interests_interest | False | 1 | interest_id | BTREE |
| ix_user_interests_interest | False | 2 | user_id | BTREE |

## user_onboarding

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| user_id | bigint unsigned | NO | PRI | NULL |  |
| purpose | varchar(30) | NO |  | NULL |  |
| category_id | bigint unsigned | NO | MUL | NULL |  |
| completed_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: category_id → categories.category_id (fk_user_onboarding_category); user_id → users.user_id (fk_user_onboarding_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | user_id | BTREE |
| fk_user_onboarding_category | False | 1 | category_id | BTREE |

## user_onboarding_topics

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| user_id | bigint unsigned | NO | PRI | NULL |  |
| interest_id | bigint unsigned | NO | PRI | NULL |  |
| selected_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |

Foreign keys: interest_id → interests.interest_id (fk_onboarding_topics_interest); user_id → users.user_id (fk_onboarding_topics_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | user_id | BTREE |
| PRIMARY | True | 2 | interest_id | BTREE |
| ix_onboarding_topics_interest | False | 1 | interest_id | BTREE |
| ix_onboarding_topics_interest | False | 2 | user_id | BTREE |

## user_preferences

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| user_id | bigint unsigned | NO | PRI | NULL |  |
| category_id | bigint unsigned | NO | PRI | NULL |  |
| preference_weight | decimal(5,4) | NO |  | 1.0000 |  |
| preference_source | varchar(20) | NO |  | USER |  |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: category_id → categories.category_id (fk_user_preferences_category); user_id → users.user_id (fk_user_preferences_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | user_id | BTREE |
| PRIMARY | True | 2 | category_id | BTREE |
| ix_user_preferences_category | False | 1 | category_id | BTREE |
| ix_user_preferences_category | False | 2 | user_id | BTREE |

## user_topic_preferences

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| user_id | bigint unsigned | NO | PRI | NULL |  |
| interest_id | bigint unsigned | NO | PRI | NULL |  |
| positive_score | decimal(10,4) | NO |  | 0.0000 |  |
| negative_score | decimal(10,4) | NO |  | 0.0000 |  |
| preference_score | decimal(6,4) | NO |  | 0.0000 |  |
| interaction_count | int unsigned | NO |  | 0 |  |
| confidence | decimal(5,4) | NO |  | 0.0000 |  |
| trend | varchar(10) | NO |  | STABLE |  |
| last_interacted_at | timestamp | YES |  | NULL |  |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |

Foreign keys: interest_id → interests.interest_id (fk_topic_preferences_interest); user_id → users.user_id (fk_topic_preferences_user).

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | user_id | BTREE |
| PRIMARY | True | 2 | interest_id | BTREE |
| ix_topic_preferences_interest_score | False | 1 | interest_id | BTREE |
| ix_topic_preferences_interest_score | False | 2 | preference_score | BTREE |
| ix_topic_preferences_user_updated | False | 1 | user_id | BTREE |
| ix_topic_preferences_user_updated | False | 2 | updated_at | BTREE |

## users

| Column | Type | Nullable | Key | Default | Extra |
|---|---|---|---|---|---|
| user_id | bigint unsigned | NO | PRI | NULL | auto_increment |
| username | varchar(50) | NO | UNI | NULL |  |
| email | varchar(254) | NO | UNI | NULL |  |
| password_hash | varchar(255) | NO |  | NULL |  |
| display_name | varchar(100) | NO |  | NULL |  |
| role | varchar(20) | NO |  | USER |  |
| created_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
| updated_at | timestamp | NO |  | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update CURRENT_TIMESTAMP |
| auth_version | int unsigned | NO |  | 1 |  |
| exploration | tinyint unsigned | NO |  | 50 |  |

Foreign keys: none.

| Index | Unique | Position | Column | Index type |
|---|---|---|---|---|
| PRIMARY | True | 1 | user_id | BTREE |
| uk_users_username | True | 1 | username | BTREE |
| uk_users_email | True | 1 | email | BTREE |
