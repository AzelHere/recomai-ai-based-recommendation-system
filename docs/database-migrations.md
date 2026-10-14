# Database migrations

The application uses Flyway to apply ordered MySQL schema migrations at startup. The scripts live in `src/main/resources/db/migration` and are versioned; do not edit a migration that has already been applied to a shared database. Add a new migration for later schema changes.

## Existing project database

The application Flyway property `baseline-on-migrate` is disabled by default. A database with valid Flyway migration history is upgraded normally. V5 adds password-reset token storage and per-user authentication versions without deleting existing rows. V6 adds nullable score-explanation JSON; existing recommendation rows remain readable with no historical breakdown.

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to the intended project database before starting the application. A legacy schema without Flyway history must be backed up and inspected first. If, and only if, the existing tables are verified to match V1, an operator may explicitly enable `FLYWAY_BASELINE_ON_MIGRATE=true` to record baseline version 1 before applying V2 onward. Do not use that setting for an unknown non-empty database; startup should fail so its schema can be reviewed.

## New database

For Docker development, `start-local.ps1` creates the database/user and starts the services; no manual table creation is required. For a separately managed empty database, create the database first, then start the application. Flyway applies V1 through V11 in order. V11 adds an optional user-scoped interaction request ID for retry-safe event recording and an index for latest-per-item progress lookup. The migration history remains append-only; the database account needs permission to create/alter tables and indexes, seed the starter rows, and create foreign keys.

## Migration inventory

| Version | File | Purpose |
|---|---|---|
| 1 | `V1__initial_schema.sql` | Preserves the original application schema as the baseline definition. |
| 2 | `V2__recommendation_schema_improvements.sql` | Adds category/interest lifecycle fields, additional item types and LIKE, and recommendation batches. |
| 3 | `V3__adaptive_interest_discovery.sql` | Adds the five supported niches, topic relations, curated resource metadata/seeds, lightweight onboarding, decaying user-topic preferences, expanded event types, and structured recommendation bucket/reason fields. |
| 4 | `V4__search_intent_signals.sql` | Allows a user-scoped search event with a bounded query string and nullable item reference; search terms contribute a small configurable signal to matching resource topics. |
| 5 | `V5__secure_password_reset.sql` | Adds the auth version used to revoke older sessions and a hash-only, expiring, single-use password reset token table. |
| 6 | `V6__recommendation_score_explanations.sql` | Stores actual ranking contributions as optional JSON, permits evidence-based reason types, and adds indexes for recent positive interaction aggregates. |
| 7 | `V7__editor_picks.sql` | Adds an explicit editorial-pick flag/index and permits the matching recommendation reason type. |
| 8 | `V8__repair_broken_catalog_urls.sql` | Repairs 13 catalog destinations confirmed to return HTTP 404; no schema change. |
| 9 | `V9__progress_and_collections.sql` | Adds explicit COMPLETE interaction and normalized owner-scoped user collections and collection items. |
| 10 | `V10__admin_recommendation_settings.sql` | Adds persisted feed mix settings and an audit trail linked to the user who changed settings. |

`src/main/resources/schema.sql` is the complete current-schema reference. Runtime schema changes are applied from the ordered migration files, not by re-running that snapshot over a database.

## Safe operation

- Back up any important database before the first migration.
- Do not delete `flyway_schema_history` to force a rerun. Correct a failed migration deliberately, then use Flyway's repair procedure only after reviewing the database state.
- V2 through V10 preserve existing account and catalog rows. V3 inserts starter resources and topic mappings once as part of migration. V4 expands the interaction event shape. V5 adds a defaulted auth-version column and a new token table. V6 adds nullable diagnostics and widens a reason constraint. V7 adds editor-pick data. V8 repairs 13 confirmed catalog URLs. V9 adds progress/collection structures. V10 inserts default recommendation shares and creates the audit trail.
- Docker Compose creates the MySQL database and application user; for non-Docker deployments, the database must exist before the application starts.
