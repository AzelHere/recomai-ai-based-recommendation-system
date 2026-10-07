# Database migrations

The application uses Flyway to apply ordered MySQL schema migrations at startup. The scripts live in `src/main/resources/db/migration` and are versioned; do not edit a migration that has already been applied to a shared database. Add a new migration for later schema changes.

## Existing project database

The current application schema is the V1 baseline. On startup, Flyway detects the existing non-empty schema, records baseline version 1, and applies V2. V2 only adds columns/tables or broadens checks. It leaves existing user, catalog, interaction, feedback, and recommendation rows in place. Existing recommendation rows keep `batch_id = NULL`, so they remain readable while later application code adopts batches.

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to the intended project database before starting the application. Baseline-on-migrate is enabled to upgrade the existing project schema automatically. Point these settings only at the recommendation-system database: Flyway baselines a non-empty schema at version 1 before applying V2.

## New database

Create the empty database first, then start the application. Flyway applies V1 followed by V2. The database account needs permission to create and alter tables and indexes, as well as to create foreign keys.

## Migration inventory

| Version | File | Purpose |
|---|---|---|
| 1 | `V1__initial_schema.sql` | Preserves the original application schema as the baseline definition. |
| 2 | `V2__recommendation_schema_improvements.sql` | Adds category/interest lifecycle fields, additional item types and LIKE, and recommendation batches. |

`src/main/resources/schema.sql` is the complete current-schema reference. Runtime schema changes are applied from the ordered migration files, not by re-running that snapshot over a database.

## Safe operation

- Back up any important database before the first migration.
- Do not delete `flyway_schema_history` to force a rerun. Correct a failed migration deliberately, then use Flyway's repair procedure only after reviewing the database state.
- V2 does not drop application tables or delete rows. Existing recommendation records are retained without a batch association.
- The application does not create the MySQL database itself; it must already exist.
