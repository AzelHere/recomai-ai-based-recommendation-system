# Recom AI post-implementation validation

Validation date: 2026-10-10.

## Persistent MySQL and Flyway

- The live Compose project was `ai-based-recommendation-system`; the app health endpoint reported both the service and database `UP`. The database was MySQL 8.4.11 on the existing named `mysql_data` volume.
- Before starting the rebuilt backend, the pre-migration database was backed up with a single-transaction MySQL dump. The dump was restored in a temporary, isolated MySQL 8.4 container and queried successfully before proceeding.
- The verified pre-migration Flyway state was V1–V5, all successful. Backend startup applied V6. Post-migration history shows V1–V6 successful and zero failed migrations.
- V6's nullable JSON explanation column, expanded recommendation reason check, and the three new query indexes were verified in MySQL metadata.
- Row counts matched before and after migration: users 15; catalog items 25; interactions 6; feedback 1; recommendation rows 40; category preferences 7. No account or catalog rows were removed. The persistent MySQL volume was not reset or deleted.

## Recommendation behavior and user experience

- Java 21 tests use deterministic synthetic cases for score arithmetic, bounded 0–100 ranking scores, category/topic and metadata matches, negative penalties, negative-item suppression, sparse and qualified collaborative evidence, popularity evidence thresholds, cold-start explanations, and deterministic ordering. These validate the rules; they do not estimate production accuracy.
- The isolated browser test changes category preference from zero to one and confirms a subsequent recommendation batch contains a positive category contribution. It verifies that a recent view suppresses the item in the next batch; repeated likes by one account do not qualify an item as popular for another account; like, hide, and not-interested actions reach the backend; hidden/disliked items stay out of the next batch; ratings persist after signing out and back in; and a second account cannot read the first account's interaction or feedback history.
- Recommendation cards now explain their actions in plain language, use readable contribution labels, and show an honest notice when some picks are exploratory. Interest-profile guidance points users to item-level feedback when an inferred topic seems wrong.
- Desktop (1440 px) and mobile (390 px) recommendation cards were reviewed. The mobile card keeps its actions below the resource description, avoids horizontal page overflow, and exposes sign-out in the compact navigation.

## Evaluation limits

The live dataset is too small for a trustworthy held-out comparison: it has 25 catalog items, 6 interactions, and 1 rating. Recommendation rows are generated candidates, not verified impressions. The application does not yet persist a visible-impression event, so click-through rate, conversion lift, and online effectiveness cannot be calculated. No real-world recommendation metric or improvement claim is made. Collect more eligible user feedback and implement idempotent visible-impression tracking before starting time-aware Precision@K, Recall@K, or NDCG evaluation.

## Verification results

- Java 21 Docker `mvn -B clean verify`: 87 tests, 0 failures, 0 errors, 0 skipped.
- Browser JavaScript syntax check: passed.
- Isolated Docker/MySQL/Playwright journey: 1 passed. The temporary test database and volume were removed by the test runner; the persistent project database was not used for those test-account flows.
- Persistent app and database health endpoint: `UP` after V6 migration and backend restart.

Run the full browser test with `npm run test:e2e`; it creates its own uniquely named Docker Compose project and cleans up only that temporary project.
