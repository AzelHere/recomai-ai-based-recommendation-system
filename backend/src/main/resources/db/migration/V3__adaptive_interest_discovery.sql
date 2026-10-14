-- Add the controlled five-niche taxonomy and adaptive discovery signals.
ALTER TABLE categories
    ADD COLUMN niche_code VARCHAR(40) NULL AFTER name,
    ADD UNIQUE INDEX uk_categories_niche_code (niche_code);

ALTER TABLE interests
    ADD COLUMN category_id BIGINT UNSIGNED NULL AFTER interest_id,
    ADD INDEX ix_interests_category_active (category_id, is_active, name),
    ADD CONSTRAINT fk_interests_category FOREIGN KEY (category_id)
        REFERENCES categories (category_id) ON UPDATE CASCADE ON DELETE SET NULL;

CREATE TABLE interest_relations (
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

ALTER TABLE items
    ADD COLUMN source_name VARCHAR(160) NULL AFTER external_url,
    ADD COLUMN difficulty VARCHAR(20) NOT NULL DEFAULT 'ALL_LEVELS' AFTER source_name,
    ADD COLUMN estimated_minutes SMALLINT UNSIGNED NULL AFTER difficulty,
    ADD COLUMN quality_score DECIMAL(5, 4) NOT NULL DEFAULT 0.5000 AFTER estimated_minutes,
    ADD INDEX ix_items_discovery (category_id, is_active, quality_score);

ALTER TABLE items
    DROP CHECK chk_items_type,
    ADD CONSTRAINT chk_items_type CHECK (
        item_type IN ('PRODUCT', 'CONTENT', 'COURSE', 'RESOURCE', 'ARTICLE', 'TOOL', 'PROJECT', 'OTHER',
                      'VIDEO', 'DOCUMENTATION', 'GUIDE')
    ),
    ADD CONSTRAINT chk_items_difficulty CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'ALL_LEVELS')),
    ADD CONSTRAINT chk_items_estimated_minutes CHECK (estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 1440),
    ADD CONSTRAINT chk_items_quality CHECK (quality_score BETWEEN 0 AND 1);

ALTER TABLE interactions
    DROP CHECK chk_interactions_type,
    ADD CONSTRAINT chk_interactions_type CHECK (
        interaction_type IN ('VIEW', 'CLICK', 'READ_PROGRESS', 'SAVE', 'PURCHASE', 'LIKE', 'DISLIKE', 'HIDE',
                             'SHARE', 'SKIP', 'MORE_LIKE')
    );

CREATE TABLE user_onboarding (
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

ALTER TABLE user_preferences
    ADD COLUMN preference_source VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER preference_weight,
    ADD CONSTRAINT chk_user_preferences_source CHECK (preference_source IN ('USER', 'ONBOARDING'));

CREATE TABLE user_onboarding_topics (
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

CREATE TABLE user_topic_preferences (
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

ALTER TABLE recommendations
    ADD COLUMN reason_type VARCHAR(30) NOT NULL DEFAULT 'PROFILE_MATCH' AFTER reason,
    ADD COLUMN bucket_type VARCHAR(20) NOT NULL DEFAULT 'FAMILIAR' AFTER reason_type,
    ADD CONSTRAINT chk_recommendations_reason_type CHECK (
        reason_type IN ('ONBOARDING', 'PROFILE_MATCH', 'RECENT_ACTIVITY', 'RELATED_INTEREST', 'DISCOVERY')
    ),
    ADD CONSTRAINT chk_recommendations_bucket_type CHECK (bucket_type IN ('FAMILIAR', 'RELATED', 'DISCOVERY'));

INSERT INTO categories (name, niche_code, description, is_active)
VALUES
    ('Technology', 'TECHNOLOGY', 'Programming, cloud, AI, security, and digital tools.', TRUE),
    ('Business & Finance', 'BUSINESS_FINANCE', 'Entrepreneurship, finance, marketing, and strategy.', TRUE),
    ('Learning & Career', 'LEARNING_CAREER', 'Study skills, career growth, and professional skills.', TRUE),
    ('Health & Fitness', 'HEALTH_FITNESS', 'Fitness, nutrition, sleep, and everyday wellbeing.', TRUE),
    ('Creative & Lifestyle', 'CREATIVE_LIFESTYLE', 'Design, writing, photography, travel, and hobbies.', TRUE)
ON DUPLICATE KEY UPDATE description = VALUES(description), niche_code = VALUES(niche_code), is_active = TRUE;

INSERT INTO interests (name, category_id, is_active)
SELECT seed.name, category.category_id, TRUE
FROM (
    SELECT 'Programming' AS name, 'TECHNOLOGY' AS niche UNION ALL
    SELECT 'Cloud Computing', 'TECHNOLOGY' UNION ALL SELECT 'AI/ML', 'TECHNOLOGY' UNION ALL
    SELECT 'Cybersecurity', 'TECHNOLOGY' UNION ALL SELECT 'DevOps', 'TECHNOLOGY' UNION ALL
    SELECT 'Databases', 'TECHNOLOGY' UNION ALL SELECT 'Web Development', 'TECHNOLOGY' UNION ALL
    SELECT 'Entrepreneurship', 'BUSINESS_FINANCE' UNION ALL SELECT 'Startups', 'BUSINESS_FINANCE' UNION ALL
    SELECT 'Personal Finance', 'BUSINESS_FINANCE' UNION ALL SELECT 'Investing Basics', 'BUSINESS_FINANCE' UNION ALL
    SELECT 'Marketing', 'BUSINESS_FINANCE' UNION ALL SELECT 'Business Strategy', 'BUSINESS_FINANCE' UNION ALL
    SELECT 'Study Skills', 'LEARNING_CAREER' UNION ALL SELECT 'Interview Preparation', 'LEARNING_CAREER' UNION ALL
    SELECT 'Career Development', 'LEARNING_CAREER' UNION ALL SELECT 'Productivity', 'LEARNING_CAREER' UNION ALL
    SELECT 'Communication', 'LEARNING_CAREER' UNION ALL SELECT 'Professional Skills', 'LEARNING_CAREER' UNION ALL
    SELECT 'Fitness', 'HEALTH_FITNESS' UNION ALL SELECT 'Nutrition Basics', 'HEALTH_FITNESS' UNION ALL
    SELECT 'Workouts', 'HEALTH_FITNESS' UNION ALL SELECT 'Wellness', 'HEALTH_FITNESS' UNION ALL
    SELECT 'Sleep', 'HEALTH_FITNESS' UNION ALL SELECT 'Healthy Habits', 'HEALTH_FITNESS' UNION ALL
    SELECT 'Design', 'CREATIVE_LIFESTYLE' UNION ALL SELECT 'Photography', 'CREATIVE_LIFESTYLE' UNION ALL
    SELECT 'Writing', 'CREATIVE_LIFESTYLE' UNION ALL SELECT 'Travel', 'CREATIVE_LIFESTYLE' UNION ALL
    SELECT 'Hobbies', 'CREATIVE_LIFESTYLE' UNION ALL SELECT 'Creativity', 'CREATIVE_LIFESTYLE'
) seed
JOIN categories category ON category.niche_code = seed.niche
ON DUPLICATE KEY UPDATE category_id = VALUES(category_id), is_active = TRUE;

INSERT INTO interest_relations (interest_id, related_interest_id, relevance_weight)
SELECT source.interest_id, related.interest_id, pair.weight
FROM (
    SELECT 'Programming' source_name, 'Web Development' related_name, 0.80 weight UNION ALL
    SELECT 'Programming','Databases',0.70 UNION ALL SELECT 'Programming','AI/ML',0.55 UNION ALL
    SELECT 'Cloud Computing','DevOps',0.90 UNION ALL SELECT 'Cloud Computing','Cybersecurity',0.65 UNION ALL
    SELECT 'Cloud Computing','Databases',0.55 UNION ALL SELECT 'DevOps','Cloud Computing',0.90 UNION ALL
    SELECT 'DevOps','Programming',0.60 UNION ALL SELECT 'AI/ML','Programming',0.80 UNION ALL
    SELECT 'AI/ML','Databases',0.60 UNION ALL SELECT 'Cybersecurity','Cloud Computing',0.65 UNION ALL
    SELECT 'Cybersecurity','Web Development',0.55 UNION ALL SELECT 'Entrepreneurship','Startups',0.90 UNION ALL
    SELECT 'Entrepreneurship','Marketing',0.55 UNION ALL SELECT 'Personal Finance','Investing Basics',0.85 UNION ALL
    SELECT 'Personal Finance','Business Strategy',0.50 UNION ALL SELECT 'Study Skills','Productivity',0.75 UNION ALL
    SELECT 'Study Skills','Career Development',0.55 UNION ALL SELECT 'Interview Preparation','Programming',0.70 UNION ALL
    SELECT 'Interview Preparation','Communication',0.65 UNION ALL SELECT 'Fitness','Workouts',0.90 UNION ALL
    SELECT 'Fitness','Healthy Habits',0.60 UNION ALL SELECT 'Nutrition Basics','Healthy Habits',0.80 UNION ALL
    SELECT 'Nutrition Basics','Wellness',0.60 UNION ALL SELECT 'Design','Creativity',0.80 UNION ALL
    SELECT 'Design','Photography',0.60 UNION ALL SELECT 'Writing','Communication',0.65 UNION ALL
    SELECT 'Writing','Creativity',0.60 UNION ALL SELECT 'Photography','Design',0.65 UNION ALL
    SELECT 'Photography','Travel',0.50
) pair
JOIN interests source ON source.name = pair.source_name
JOIN interests related ON related.name = pair.related_name
ON DUPLICATE KEY UPDATE relevance_weight = VALUES(relevance_weight);

-- Metadata only: links point to public learning resources; no copyrighted articles are copied.
INSERT INTO items (category_id, title, description, item_type, external_url, source_name, difficulty, estimated_minutes, quality_score)
SELECT category.category_id, seed.title, seed.description, seed.item_type, seed.url, seed.source, seed.difficulty, seed.minutes, seed.quality
FROM (
    SELECT 'TECHNOLOGY' niche, 'Getting Started with Java' title, 'A guided introduction to Java syntax, classes, and core concepts.' description, 'GUIDE' item_type, 'https://dev.java/learn/' url, 'dev.java' source, 'BEGINNER' difficulty, 20 minutes, 0.94 quality UNION ALL
    SELECT 'TECHNOLOGY','Spring Guides: Building a RESTful Web Service','Build a small Java web service with Spring.','DOCUMENTATION','https://spring.io/guides/gs/rest-service/','Spring','BEGINNER',30,0.96 UNION ALL
    SELECT 'TECHNOLOGY','MDN: Getting Started with the Web','Learn the core building blocks of websites.','DOCUMENTATION','https://developer.mozilla.org/en-US/docs/Learn/Getting_started_with_the_web','MDN','BEGINNER',45,0.96 UNION ALL
    SELECT 'TECHNOLOGY','Docker Get Started','Learn the basics of containerizing and running applications.','DOCUMENTATION','https://docs.docker.com/get-started/','Docker','BEGINNER',35,0.95 UNION ALL
    SELECT 'TECHNOLOGY','AWS Cloud Practitioner Essentials','Explore foundational cloud concepts and AWS services.','COURSE','https://aws.amazon.com/training/digital/aws-cloud-practitioner-essentials/','AWS','BEGINNER',60,0.93 UNION ALL
    SELECT 'TECHNOLOGY','OWASP Top Ten','Understand common web application security risks.','DOCUMENTATION','https://owasp.org/www-project-top-ten/','OWASP','INTERMEDIATE',25,0.94 UNION ALL
    SELECT 'BUSINESS_FINANCE','The Lean Startup: Build-Measure-Learn','A practical introduction to testing early business ideas.','ARTICLE','https://www.strategyzer.com/library/the-lean-startup','Strategyzer','BEGINNER',15,0.82 UNION ALL
    SELECT 'BUSINESS_FINANCE','Personal Finance Basics','Learn budgeting, saving, and managing everyday money.','GUIDE','https://www.consumerfinance.gov/consumer-tools/educator-tools/','CFPB','BEGINNER',30,0.93 UNION ALL
    SELECT 'BUSINESS_FINANCE','Investing Basics','Understand risk, diversification, and long-term investing concepts.','GUIDE','https://www.investor.gov/introduction-investing','Investor.gov','BEGINNER',25,0.95 UNION ALL
    SELECT 'BUSINESS_FINANCE','Marketing Strategy Essentials','Explore audience, positioning, and marketing planning.','COURSE','https://academy.hubspot.com/courses/digital-marketing','HubSpot Academy','BEGINNER',50,0.89 UNION ALL
    SELECT 'BUSINESS_FINANCE','Business Model Canvas','Map the key parts of a business idea on one page.','RESOURCE','https://www.strategyzer.com/library/the-business-model-canvas','Strategyzer','BEGINNER',20,0.91 UNION ALL
    SELECT 'LEARNING_CAREER','Learning How to Learn','Study strategies for building durable understanding.','COURSE','https://www.coursera.org/learn/learning-how-to-learn','Coursera','BEGINNER',60,0.94 UNION ALL
    SELECT 'LEARNING_CAREER','Technical Interview Preparation','Practice structured approaches to technical interviews.','RESOURCE','https://www.pramp.com/','Pramp','INTERMEDIATE',30,0.87 UNION ALL
    SELECT 'LEARNING_CAREER','Career Development Toolkit','Explore practical resources for planning your career.','GUIDE','https://www.careeronestop.org/','CareerOneStop','BEGINNER',25,0.92 UNION ALL
    SELECT 'LEARNING_CAREER','Deep Work and Focus','Ideas for protecting focused time and reducing distraction.','ARTICLE','https://www.calnewport.com/books/deep-work/','Cal Newport','BEGINNER',15,0.82 UNION ALL
    SELECT 'LEARNING_CAREER','Communication Skills at Work','Practice clearer communication in professional settings.','COURSE','https://www.coursera.org/learn/wharton-communication-skills','Coursera','BEGINNER',45,0.90 UNION ALL
    SELECT 'HEALTH_FITNESS','Move Your Way Activity Planner','Build a simple activity plan that fits your routine.','RESOURCE','https://health.gov/moveyourway/activity-planner','Move Your Way','BEGINNER',15,0.96 UNION ALL
    SELECT 'HEALTH_FITNESS','Nutrition Basics','Learn the basics of balanced eating from a public health source.','GUIDE','https://www.nhs.uk/live-well/eat-well/','NHS','BEGINNER',20,0.93 UNION ALL
    SELECT 'HEALTH_FITNESS','Strength Training Basics','Review safe, beginner-friendly strength training guidance.','GUIDE','https://www.cdc.gov/physical-activity-basics/guidelines/adults.html','CDC','BEGINNER',20,0.95 UNION ALL
    SELECT 'HEALTH_FITNESS','Sleep Hygiene','Understand habits that support healthy sleep.','GUIDE','https://www.nhlbi.nih.gov/health/sleep-deprivation/healthy-sleep-habits','NHLBI','BEGINNER',15,0.94 UNION ALL
    SELECT 'HEALTH_FITNESS','Stress and Wellbeing','Explore evidence-informed ways to support mental wellbeing.','RESOURCE','https://www.who.int/news-room/fact-sheets/detail/mental-health-strengthening-our-response','WHO','ALL_LEVELS',20,0.94 UNION ALL
    SELECT 'CREATIVE_LIFESTYLE','Design Principles for Non-Designers','Learn visual hierarchy, contrast, and layout fundamentals.','COURSE','https://www.canva.com/designschool/','Canva Design School','BEGINNER',25,0.86 UNION ALL
    SELECT 'CREATIVE_LIFESTYLE','Photography Basics','Practice composition and exposure with a beginner guide.','GUIDE','https://www.adobe.com/creativecloud/photography/discover/photography-basics.html','Adobe','BEGINNER',20,0.88 UNION ALL
    SELECT 'CREATIVE_LIFESTYLE','Writing in Plain Language','Make writing clearer and easier to understand.','GUIDE','https://www.plainlanguage.gov/guidelines/','PlainLanguage.gov','BEGINNER',20,0.94 UNION ALL
    SELECT 'CREATIVE_LIFESTYLE','Creative Confidence Exercises','Try small exercises to build a regular creative practice.','RESOURCE','https://www.ideou.com/pages/creative-confidence','IDEO U','BEGINNER',20,0.84
) seed
JOIN categories category ON category.niche_code = seed.niche;

INSERT INTO item_interests (item_id, interest_id, relevance_weight)
SELECT item.item_id, interest.interest_id, 1.0000
FROM items item
JOIN categories category ON category.category_id = item.category_id
JOIN interests interest ON interest.category_id = category.category_id
WHERE (item.title LIKE '%Java%' AND interest.name = 'Programming')
   OR (item.title LIKE '%Spring%' AND interest.name IN ('Programming', 'Web Development'))
   OR (item.title LIKE '%Web%' AND interest.name = 'Web Development')
   OR (item.title LIKE '%Docker%' AND interest.name = 'DevOps')
   OR (item.title LIKE '%AWS%' AND interest.name = 'Cloud Computing')
   OR (item.title LIKE '%OWASP%' AND interest.name = 'Cybersecurity')
   OR (item.title LIKE '%Startup%' AND interest.name IN ('Entrepreneurship', 'Startups'))
   OR (item.title LIKE '%Finance%' AND interest.name = 'Personal Finance')
   OR (item.title LIKE '%Investing%' AND interest.name = 'Investing Basics')
   OR (item.title LIKE '%Marketing%' AND interest.name = 'Marketing')
   OR (item.title LIKE '%Model Canvas%' AND interest.name = 'Business Strategy')
   OR (item.title LIKE '%Learning%' AND interest.name = 'Study Skills')
   OR (item.title LIKE '%Interview%' AND interest.name = 'Interview Preparation')
   OR (item.title LIKE '%Career%' AND interest.name = 'Career Development')
   OR (item.title LIKE '%Deep Work%' AND interest.name = 'Productivity')
   OR (item.title LIKE '%Communication%' AND interest.name = 'Communication')
   OR (item.title LIKE '%Activity Planner%' AND interest.name IN ('Fitness', 'Workouts'))
   OR (item.title LIKE '%Nutrition%' AND interest.name = 'Nutrition Basics')
   OR (item.title LIKE '%Strength%' AND interest.name = 'Workouts')
   OR (item.title LIKE '%Sleep%' AND interest.name = 'Sleep')
   OR (item.title LIKE '%Stress%' AND interest.name = 'Wellness')
   OR (item.title LIKE '%Design%' AND interest.name = 'Design')
   OR (item.title LIKE '%Photography%' AND interest.name = 'Photography')
   OR (item.title LIKE '%Writing%' AND interest.name = 'Writing')
ON DUPLICATE KEY UPDATE relevance_weight = VALUES(relevance_weight);
