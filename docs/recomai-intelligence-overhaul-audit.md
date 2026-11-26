# Recom AI intelligence overhaul: audit and implementation record

## Baseline audit (2026-10-10)

The application is an existing Java 21 / Spring MVC / Spring JDBC / MySQL 8.4 application with a vanilla JavaScript client, server-side sessions, Flyway migrations V1–V5, and Docker Compose. The working tree already contained extensive changes from prior project phases; those changes are preserved.

### What is implemented

- Registration, login, onboarding, explicit category/topic interests, catalog browsing/search, feedback ratings, interaction capture, inferred topic profiles, recommendation batches, admin analytics, and password reset are already represented in controller/service/DAO layers.
- `InteractionService` persists observed item/search events and updates `user_topic_preferences`; `TopicPreferenceScorer` decays evidence over time and distinguishes positive and negative events.
- `JdbcRecommendationDao.findCandidates` joins explicit preferences, onboarding, topic behavior, ratings, item-interest metadata, and item quality metadata. `ScoringRecommendationEngine` ranks candidates, filters strongly negative items, selects familiar/related/discovery buckets, and generates human-readable reasons.
- Recommendation batches and algorithm versions are persisted. Feedback and activity are scoped to the authenticated user.
- Existing data is versioned by additive Flyway migrations. The app continues to use the existing database and persistent Docker volume.

### Gaps found

- The engine is a weighted heuristic, not collaborative filtering or text-vector similarity. The item catalog is small and the recorded interaction history is sparse, so collaborative signals must be gated rather than presented as learned evidence.
- The score is a ranking score on a 0–100 scale, not a calibrated probability. The interface rendered it as a percentage, which implied confidence the system does not measure.
- Recommendation explanation was a single string; the actual component contributions were not stored or inspectable.
- “Discovery” could rely on the default item-quality score (0.5), which is not enough evidence to call an item popular or high quality.
- The current UI has preference/activity controls and recommendation actions, but it does not yet provide a clear score-contribution disclosure or a protected evaluation report.

### Data and verification baseline

No production-like database record was modified during this audit. Docker requires an approved elevated process in this environment; the isolated verification below was run using a temporary Compose project. The local JVM is Java 26, so Java 21 verification was run inside the project's Docker builder rather than against the local runtime. The main development database was deliberately not migrated; no pre-migration live counts were collected for it in this session.

## Execution phases and verification criteria

1. **Engine and diagnostics:** normalize component signals, add a gated item-item/user-similarity signal that remains zero when evidence is sparse, remove misleading probability language, and persist score contributions. Verify scoring and sparse-data behavior with unit tests.
2. **Cold start and feedback:** retain onboarding and incremental topic updates; ensure weak clicks cannot outweigh explicit negative feedback; ensure exclusions and updated preferences affect the next generated batch. Verify service and API ownership tests.
3. **Experience and transparency:** expose explanation contributions and honest evidence labels in the current UI, retaining existing navigation and actions. Verify accessible rendering and browser journeys.
4. **Evaluation and operations:** add offline ranking metrics only when eligible historical labels exist, document limitations, and verify migration/build/real-browser behavior in Java 21 Docker with an isolated MySQL database. No engagement lift is claimed without online data.

## Algorithm boundary

The current engine remains an explainable weighted ranker. The collaboration feature added for this overhaul is an implicit user-neighborhood signal based on shared positive item overlap, with a minimum evidence threshold; it is a conventional neighborhood heuristic, not matrix factorization or a trained neural model. When the threshold is not met, its contribution is exactly zero and the explanation says the evidence is insufficient. All displayed contribution values are ranking points, not probabilities.

## Implementation and verification update (2026-10-10)

- **Implemented:** bounded candidate SQL; title/description/category/topic TF-IDF cosine from positive item history; 90-day cross-user popularity with a three-user evidence gate; 180-day overlap-based collaborative evidence requiring at least two common positive items and three distinct neighbors for a candidate; negative feedback penalty and explicit dislike/hide suppression; 14-day suppression for recent views/progress/clicks; deterministic score breakdown persistence under algorithm version `hybrid-evidence-v2`; and user-visible evidence explanations with no probability label.
- **Migration:** V6 adds nullable JSON explanation metadata, widens the valid reason types, and adds aggregate-query indexes. Before restarting the persistent backend, a transaction-consistent MySQL dump was restored and queried in an isolated MySQL 8.4 container. Flyway then applied V6 successfully to the persistent MySQL 8.4.11 database; V1–V6 are successful with no failed migrations. The users, catalog, interaction, feedback, recommendation, and preference row counts match before and after. See [the post-implementation validation record](recomai-quality-validation.md).
- **Tests and product quality:** the Java 21 Docker `mvn clean verify` completed with 87 passing tests. The final isolated Playwright journey passed with responsive card-order and no-overflow checks, mobile sign-out, category-preference score changes, Like/Hide/Not interested actions, hidden/disliked-item exclusion, and cross-account history isolation. Its temporary MySQL volume was removed afterward. Recommendation controls and signal names were clarified in the existing UI.
- **Still limited:** the persistent dataset has 25 catalog items, 6 interactions, and 1 feedback rating, so it is insufficient to claim collaborative lift or compare algorithms using a trustworthy time split. No verified impression event exists, so CTR and online recommendation-effectiveness claims are unavailable. Recommendation behavior was not benchmarked at large-catalog scale; the current SQL candidate cap is 1,000.
