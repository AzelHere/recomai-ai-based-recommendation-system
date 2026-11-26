-- Additive provenance only: original resource IDs and all owner relationships remain intact.
ALTER TABLE items DROP CHECK chk_items_difficulty,
    ADD CONSTRAINT chk_items_difficulty CHECK (difficulty IN ('BEGINNER','INTERMEDIATE','ADVANCED','ALL_LEVELS','UNKNOWN'));
INSERT IGNORE INTO interests(category_id,name)
SELECT c.category_id,t.name FROM categories c JOIN (
 SELECT 'TECHNOLOGY' niche,'Java' name UNION ALL SELECT 'TECHNOLOGY','SQL'
 UNION ALL SELECT 'TECHNOLOGY','Algorithms and Data Structures'
 UNION ALL SELECT 'TECHNOLOGY','Computer Science' UNION ALL SELECT 'TECHNOLOGY','Databases'
 UNION ALL SELECT 'TECHNOLOGY','Machine Learning' UNION ALL SELECT 'LEARNING_CAREER','Academic Writing'
) t ON c.niche_code=t.niche;
CREATE TABLE catalog_sources (
    source_id VARCHAR(40) NOT NULL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    official_url VARCHAR(2048) NOT NULL,
    license VARCHAR(80) NOT NULL,
    license_url VARCHAR(2048) NOT NULL,
    attribution_requirement VARCHAR(500) NOT NULL,
    last_import_at TIMESTAMP NULL,
    last_check_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO catalog_sources(source_id,name,official_url,license,license_url,attribution_requirement) VALUES
('mit-ocw','MIT OpenCourseWare','https://ocw.mit.edu/','CC-BY-NC-SA-4.0','https://ocw.mit.edu/pages/privacy-and-terms-of-use/','Credit MIT OCW and identified faculty, link source and license; noncommercial, share alike; excludes third-party exceptions'),
('medlineplus','MedlinePlus, National Library of Medicine','https://medlineplus.gov/','US-GOV-PUBLIC-DOMAIN','https://medlineplus.gov/about/using/usingcontent/','Source: MedlinePlus, National Library of Medicine. Only public-domain health-topic summaries; no licensed encyclopedia, drugs or images');

CREATE TABLE catalog_import_batches (
    batch_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    dataset_sha256 CHAR(64) NOT NULL,
    imported_count INT NOT NULL,
    skipped_count INT NOT NULL,
    review_count INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_catalog_batch_digest(dataset_sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_provenance (
    item_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    source_id VARCHAR(40) NOT NULL,
    external_id VARCHAR(160) NOT NULL,
    canonical_url VARCHAR(2048) NOT NULL,
    canonical_sha256 CHAR(64) NOT NULL,
    language VARCHAR(16) NULL,
    license VARCHAR(80) NOT NULL,
    attribution TEXT NOT NULL,
    metadata_origin TEXT NOT NULL,
    source_topics JSON NOT NULL,
    batch_id BIGINT UNSIGNED NOT NULL,
    acquired_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    url_status VARCHAR(32) NOT NULL DEFAULT 'NOT_CHECKED',
    url_checked_at TIMESTAMP NULL,
    final_url VARCHAR(2048) NULL,
    UNIQUE KEY uk_provenance_source_external(source_id,external_id),
    UNIQUE KEY uk_provenance_canonical(canonical_sha256),
    INDEX ix_provenance_url_check(url_checked_at,item_id),
    CONSTRAINT fk_provenance_item FOREIGN KEY(item_id) REFERENCES items(item_id) ON DELETE CASCADE,
    CONSTRAINT fk_provenance_source FOREIGN KEY(source_id) REFERENCES catalog_sources(source_id),
    CONSTRAINT fk_provenance_batch FOREIGN KEY(batch_id) REFERENCES catalog_import_batches(batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE catalog_import_reviews (
    source_id VARCHAR(40) NOT NULL,
    external_id VARCHAR(160) NOT NULL,
    title VARCHAR(150) NOT NULL,
    canonical_url VARCHAR(2048) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(source_id,external_id),
    CONSTRAINT fk_import_review_source FOREIGN KEY(source_id) REFERENCES catalog_sources(source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
