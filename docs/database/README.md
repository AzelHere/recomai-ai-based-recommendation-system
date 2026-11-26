# Recom AI database

MySQL 8.4 stores 29 application tables plus Flyway history. [Schema-only reference](../technical/schema-only.sql) · [table details](../technical/Database-Reference.md) · [immutable migrations](../../backend/src/main/resources/db/migration/).

```mermaid
erDiagram
 users ||--o{ user_preferences : owns
 users ||--o{ user_interests : selects
 interests ||--o{ user_interests : targets
 categories ||--o{ items : groups
 items ||--o{ item_interests : tagged
 interests ||--o{ item_interests : describes
 users ||--o{ interactions : records
 items ||--o{ interactions : receives
 users ||--o{ recommendations : receives
 items ||--o{ recommendations : ranks
 users ||--o{ user_collections : owns
 user_collections ||--o{ user_collection_items : contains
 items ||--o{ user_collection_items : belongs
 users ||--o{ learning_journeys : owns
 learning_journeys ||--o{ learning_journey_steps : contains
 items ||--o{ learning_journey_steps : references
 items ||--o| resource_provenance : identifies
 catalog_sources ||--o{ resource_provenance : attributes
 catalog_import_batches ||--o{ resource_provenance : imports
```

This overview shows core relationships, not every column. The schema reference supplies primary keys, foreign keys, uniqueness and indexes. Owner predicates protect private collections, activity and journeys. Source identities and canonical hashes prevent repeat imports; ambiguous editions go to review. Service transactions and locks serialize dependent writes. Never edit applied V1–V18, reset a valuable database or run `docker compose down -v` against it. New corrections require V19 or later.
