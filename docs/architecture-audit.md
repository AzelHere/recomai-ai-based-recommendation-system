# Architecture Audit — AI-Based Recommendation System

> Historical snapshot: this Phase 1 read-only audit predates the later completed implementation phases. Its gap list describes the repository at the audit date, not the current feature set. See the README for current architecture and capabilities.

**Audit scope:** Phase 1 (repository audit only)
**Project topic:** AI-Based Recommendation System
**Reviewed:** 2026-10-07

## Executive summary

The repository contains a substantial Java 21 and Spring Boot application with a layered MVC/API design, JDBC DAOs, a MySQL schema, a scoring recommendation engine, and a browser frontend. The core structure fits the project’s stated stack and should be preserved.

The main implementation gaps are around completeness of user-facing workflows and signal coverage: feedback/rating and interest management are represented in the database but do not have full application workflows; catalog discovery is basic; and the admin area does not include the activity analytics described in project notes. The recommendation engine is explainable and modular, but cold-start results and repeated recommendation history need attention.

This phase inspected files and configuration. It did not change application code and did not run a fresh build or test suite. The README records a prior mvnw clean verify with 25 passing tests; that result was not independently rechecked during this audit. A live MySQL and full browser-to-database integration run is also not recorded as verified.

## Repository map

| Area | Location | Current role |
|---|---|---|
| Build and runtime configuration | pom.xml, .mvn/wrapper/, Dockerfile | Maven wrapper, Java 21 / Spring Boot build, container packaging |
| Application configuration | src/main/resources/application.properties | Datasource, SQL initialization, server and cookie settings |
| Database schema | src/main/resources/schema.sql | MySQL tables, constraints, and indexes |
| Web/API controllers | src/main/java/.../controller/ | Authentication, users, catalog, interactions, recommendations |
| Services and validation | src/main/java/.../service/ | User, catalog, signal, and recommendation workflows |
| JDBC access | src/main/java/.../dao/ | Parameterized SQL and row mapping through Spring JDBC |
| Domain and request models | src/main/java/.../model/ | Java records for domain and request/response data |
| Recommendation logic | src/main/java/.../recommendation/ | Engine interface and scoring implementation |
| Static frontend | src/main/resources/static/ | HTML, CSS, and JavaScript application |
| Tests | src/test/java/ | Unit and web-layer tests |
| Project and submission material | README.md, docs/, submission/ | Setup, phase notes, slides, and submission checklist |

## What is working and should be preserved

### Architecture and Java implementation

- The application separates controllers, services, DAOs, models, and recommendation logic rather than putting the application in one class.
- Spring MVC controllers expose the application through HTTP endpoints, and the application uses embedded Tomcat through Spring Boot.
- Services own the main validation and workflow decisions; DAOs use JdbcTemplate, placeholders, and prepared statements for database operations.
- Java records provide concise immutable data models. The recommendation engine is behind an interface, making alternative algorithms possible without tying the service to one implementation.
- Centralized exception handling returns a consistent API error shape and avoids exposing unexpected exception details.
- Login uses server-side HTTP sessions. Session access is centralized, logout invalidates the session, and admin-only operations check the role.
- Passwords are hashed with PBKDF2-HMAC-SHA256, a per-password random salt, and constant-time comparison. Plain-text password storage is not used.

### Database and recommendation engine

- schema.sql defines normalized tables for users, categories, items, preferences, interests, interactions, feedback, and persisted recommendations, with primary keys, foreign keys, uniqueness rules, checks, and supporting indexes.
- The recommendation engine uses category preference, interest, ratings, and positive/negative interaction signals; it sorts deterministically, supports bounded top-N requests, and provides an explanation.
- Recommendation weights and scoring behavior are localized in the scoring implementation, which keeps the current algorithm understandable and replaceable.
- Catalog and user-related DAOs use parameterized SQL rather than building queries from user-provided values.

### Web interface and tests

- The static site includes public landing/auth screens and an authenticated dashboard for browsing, preferences, recommendations, interaction history, profile display name, and admin catalog operations.
- The frontend is vanilla HTML/CSS/JavaScript, consistent with the requested stack. It includes responsive styling, reduced-motion handling, input feedback, and API error/loading feedback.
- The test suite contains 25 test methods across recommendation scoring/service, accounts, preferences/interactions, catalog validation, password hashing, sessions, and authentication web behavior, matching the README's recorded test count.

## Findings and gaps

### Data model and database lifecycle

- **Partial — schema evolution:** SQL initialization creates missing tables on application startup, but there is no versioned migration mechanism. Changes to an existing table definition will not automatically migrate an already-created database.
- **Partial — interest workflows:** interests, user_interests, and item_interests exist in the schema, but there is no complete interest model/DAO/service/API workflow to manage those relationships.
- **Partial — feedback workflow:** feedback is present in the schema and contributes to recommendation signals, but there is no complete feedback submission and retrieval workflow in the web/API layer.
- **Partial — seed data:** No deterministic demo catalog, categories, or interests are installed. A new database can start empty and produce an empty catalog until data is entered.
- **Minor — schema consistency:** Interest rows have no active/timestamp fields, while categories and other data use lifecycle timestamps. Categories have a creation timestamp but no update timestamp.

### API and services

- **Partial — input/business validation:** Registration, catalog, preference bounds, interaction types, and recommendation limits are validated. Preference writes do not explicitly validate that the target category is active before persistence; the database only enforces that the category ID exists.
- **Partial — discovery:** Catalog routes support basic item/category listing and item detail, but there is no implemented search, sorting, or pagination workflow.
- **Partial — administration:** Admin APIs support category and item management. User administration and activity analytics described in project notes are not present.
- **Partial — route coverage:** There is no complete feedback or interest API, and no admin activity reporting API.
- **Observation — auth model:** Authentication uses explicit session checks rather than Spring Security. That is a coherent, lightweight choice for the current app, but authorization behavior should continue to be covered when routes are added.

### Recommendation behavior

- **Partial — cold start:** Users without preferences, interests, or interaction history can receive zero-score candidates ordered by item ID. There is no explicit popularity/default fallback or onboarding path to make these results useful.
- **Partial — signal breadth:** Scoring uses current stored signals, but has no recency decay, popularity signal, or diversity control. Signal values and weights are currently fixed in code.
- **Partial — persistence semantics:** Each generation inserts recommendation rows. The latest-results query can return repeated item recommendations across generations, and the schema does not identify a generation batch.
- **Partial — explanation/output:** The result exposes one reason string rather than a structured list of contributing signals. This is readable for a basic demo, but less useful for detailed transparency or future frontend explanation.
- **Scale risk:** Candidate loading considers all active items, without pagination or a candidate-retrieval strategy. This is reasonable for a small academic dataset but will need limits if the catalog grows.

### Frontend

- **Partial — search controls:** The discovery search control does not execute or persist a catalog search; it prompts the user to register.
- **Partial — item details:** The item “View” action records a view interaction but does not open an item detail view or follow the item's external URL.
- **Missing — feedback and ratings:** There is no visible rating/feedback submission flow, despite the feedback table and project requirements.
- **Missing — interest onboarding:** Users cannot select interests in the interface because the corresponding application workflow is absent.
- **Partial — navigation and saved content:** There is no dedicated saved-items view or routed recommendations/catalog pages; the dashboard is the primary application surface.
- **Partial — recommendation content:** Cards show minimal item data and do not expose a structured explanation of the contributing signals.
- **Partial — test coverage:** Automated tests cover core service and selected web behaviors, but there is no end-to-end browser test covering registration through persisted recommendations against MySQL.

### Documentation and repository hygiene

- **Needs correction — scope statement:** The README says “Phases 0 through 10 are prepared,” which can imply completion. Its limitations section is more accurate; align the summary wording with implemented and verified functionality.
- **Needs cleanup — submission artifact:** submission/ contains an Office temporary lock file named ~$AI-Based-Recommendation-System-GUVI-Project-Submission.pptx. It is not application source and should be removed from a clean submission in a later cleanup step.
- **Verification record:** README records a prior clean verify with 25 passing tests. This audit did not execute that command, and no live MySQL/browser integration verification is claimed here.

## Recommended work order after Phase 1

1. Verify the current baseline with the Maven wrapper and inspect/fix any build or test failures before feature work.
2. Define the MySQL setup and migration/initialization strategy, and add a small deterministic dataset for local demonstration.
3. Complete interest and feedback models, JDBC DAOs, services, validation, and API endpoints.
4. Complete catalog search/detail/navigation and feedback/interest frontend workflows.
5. Improve recommendation cold-start behavior, generation history semantics, and explanation structure.
6. Add integration-level verification for the important account, catalog, interaction, and recommendation flows.
7. Update the README and submission material to describe only implemented and verified behavior.

## Phase 1 boundary and verification

This document is the only intended deliverable for Phase 1. No application source, schema, build configuration, or frontend files were modified for this audit. The audit used repository inspection; it did not run Maven, connect to MySQL, or claim a live deployment check. Build and test verification should be the first action in the next implementation phase.

## Phase 6A follow-up (2026-10-08)

The Phase 1 observations above are a historical snapshot and have been superseded by later phases. The Phase 6A audit found that registration persisted a user but did not start a session; the frontend switched to the login tab and left new users outside the expected onboarding flow. Registration now starts a session, and the frontend confirms that session before opening the application. A dedicated Saved resources view was added over the existing interaction history. Live Docker MySQL verification covered registration through onboarding, recommendations, activity signals, interest insights, saved-item history, logout, and re-login. The current implementation retains five top-level niches and 25 curated resources. See [README.md](../README.md) for current architecture and run instructions.
