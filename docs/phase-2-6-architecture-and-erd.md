# Recom AI Phases 2–6: architecture and data map

## Request and data flow

```text
Browser (plain HTML/CSS/ES modules, same-origin session cookie)
    -> Spring MVC controller (session checks and request binding)
    -> service (validation and transaction boundaries)
    -> recommendation strategy + JDBC DAO interfaces
    -> Spring JDBC parameterized SQL
    -> MySQL 8.4 (Flyway V1–V11)
```

Recommendation explanations are generated from the score breakdown returned by the ranker. Feed share settings are persisted, read when the application is ready, applied to the ranker, and recorded in the admin audit table. Preview ranks a sample user's candidate set with temporary shares and does not save a batch or settings.

## Added relationships

```text
users 1---* user_collections 1---* user_collection_items *---1 items
users 1---* recommendation_settings_audit
users 0---* recommendation_system_settings (updated_by; nullable)
```

`V9__progress_and_collections.sql` expands the interaction enum with `COMPLETE` and adds `user_collections` plus `user_collection_items`. Both collection tables have user/item foreign keys, cascade on owner deletion, and prevent duplicate item membership in a collection. `V10__admin_recommendation_settings.sql` creates three normalized feed-share rows and the append-only settings audit history. All migrations are additive; V1–V8 are preserved.

## Current API additions

| Method and path | Purpose | Access |
|---|---|---|
| `GET/POST /api/users/me/collections` | List or create the current user's named collection | Signed-in user |
| `PUT/DELETE /api/users/me/collections/{collectionId}/items/{itemId}` | Add/remove an item after ownership checks | Signed-in owner |
| `DELETE /api/users/me/collections/{collectionId}` | Delete an owned collection | Signed-in owner |
| `GET /api/users/me/export` | Download a password-free JSON export | Signed-in user |
| `DELETE /api/users/me/personalization` | Clear personalization records while preserving account and collections | Signed-in user |
| `GET/PUT /api/admin/settings` | Read/update validated familiar, related, discovery shares | Administrator |
| `POST /api/admin/settings/preview` | Preview top-10 for an existing sample account using temporary shares | Administrator |
| `GET /api/admin/settings/audit?limit=25` | Read the bounded settings audit history | Administrator |
| `GET /api/recommendation-info` | Expose the current explanation inputs and thresholds | Public explanatory metadata |

## Phase requirements not yet represented as implemented

- Admin analytics does not yet track recommendation impressions or derive CTR/save-rate, 7/30/90-day recommendation effectiveness, category exposure-versus-engagement, or signup/return funnel metrics. It reports recorded database aggregates only.
- There is no admin user search/activation/role-management view and no synthetic demo-data generator/purger.
- Admin settings currently cover the familiar/related/discovery mix. Signal weights, decay windows, and popularity evidence gates remain code/configuration settings.
- The user interface has saved collections and progress events, but does not yet include side-by-side comparison, topic-weight sliders, a complete guided tour, or undo for feedback.
- The frontend uses ES modules for shared API/theme utilities and hash-based panels. The existing large application controller has not yet been split into separate router/user/admin view modules, and loading states and mobile bottom navigation are not comprehensive.

These are explicit follow-up gaps, not capabilities to claim in a demonstration. Admin analytics must continue to distinguish available measurements from metrics that need new event instrumentation.
