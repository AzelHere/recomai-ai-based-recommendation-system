# Offline evaluation

Run the synthetic integration check from the repository root with Docker Desktop, Java 21 and PowerShell 7:

```powershell
pwsh -NoProfile -File scripts/test-offline-evaluation.ps1
```

This starts a unique Compose project, seeds one test account, runs the actual JDBC candidate queries and Java ranker against MySQL 8.4, and prints aggregate results. It stops its containers and retains its volume. Its single future save is an artificial test case.

## Preparing a real evaluation

1. Choose a global UTC cutoff T and later end E before looking at results. Retain an immutable, transactionally consistent backup taken at T, including catalog metadata, tag mappings, ratings, user interests, admin settings and exploration preferences. Record its checksum and the application revision/image digest. Restore it on a separate local MySQL server under a name starting with `recomai_eval_`. Use a SELECT-only account. The evaluator uses a read-only transaction. Timestamp checks detect some incorrect restores, but cannot establish backup provenance. Present-day aggregate topic profiles cannot reconstruct an earlier state.
2. On a **separate later snapshot** with the same ID mapping, export UTF-8 tab-separated rows without headers, sorted by user/time. Use UTC and an explicit fixed window. For example, bind T and E to the two placeholders in this query. Do not load the resulting rows into the training database.

```sql
SET SESSION time_zone = '+00:00';
SELECT user_id, item_id, DATE_FORMAT(occurred_at,'%Y-%m-%dT%H:%i:%sZ'), interaction_type
FROM interactions
WHERE occurred_at > ? AND occurred_at <= ? AND item_id IS NOT NULL
  AND interaction_type IN ('SAVE','LIKE','COMPLETE')
ORDER BY user_id, occurred_at, interaction_id;
```

3. Keep backups, credentials, raw outcome files and their checksums privately outside Git. Supply the read-only credentials via EVAL_USER and EVAL_PASSWORD. Build the JAR using the Maven wrapper and run this command with your actual isolated database, port, times and private file:

```powershell
.\mvnw.cmd -f backend/pom.xml clean verify
java '-Dloader.main=com.guvi.recommendationsystem.evaluation.OfflineEvaluation' -cp backend/target/ai-based-recommendation-system-0.1.0-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher REAL recomai_eval_snapshot 3308 2026-11-01T00:00:00Z 2026-11-15T00:00:00Z C:\private\outcomes.tsv
```

The MySQL session clock is fixed at T, including for decay and exposure suppression. Users need training interaction history and at least one eligible positive in (T,E]. Repeated positive events on one user/item form one binary label. Clicks, views and ratings are excluded from outcome labels. Earlier ratings still contribute to the hybrid ranker. A global cutoff keeps every neighbor's future behavior outside training. Cached recommendation batches are never read. Outcomes select the evaluation cohort and score rankings only.

Both methods use the production DAO's bounded candidate pool and rejection rules. The hybrid uses the production TF-IDF, collaborative, popularity, scoring and bucket selection code with documented default signal weights, snapshot admin shares and the user's exploration setting. The baseline orders candidates by the number of distinct **other** users with a positive popularity event in the preceding 90 days, then ascending item ID. Each method generates ten picks and the first five define its @5 results. This evaluates ranking conditional on retrieval. Unreachable labels are excluded, so candidate retrieval failures do not lower recall.

## Reading the report

Precision = unique relevant hits / K, even for a short list. Recall = hits / unique eligible held-out positives. Binary NDCG discounts rank r by 1/log2(r+1) and divides by the ideal DCG for that user at K. These three metrics are averaged equally across eligible users. Coverage divides distinct items reached by the union of eligible candidate IDs across those users. Category diversity averages the proportion of pairs with different catalog categories for users with at least two recommendations. Undefined denominators display N/A. Results include dataset counts, UTC times, configuration and outcome checksum. Limits are 500 training users, 10 MB of outcome data, and five seconds per SELECT.

## Verification on 21 November 2026

Real local inventory contained 15 accounts, 200 resources, 14 interactions and one feedback row. Zero interactions qualified as outcome positives. All real comparison metrics are N/A.

The synthetic smoke run had one training view, 199 eligible candidates and one artificial held-out save. Results below are solely a reproducible integration check. All popularity counts tied, so item ID determined the baseline order. The fixture deliberately chose the lowest active item ID.

| Metric | Hybrid @5 | Popularity @5 | Hybrid @10 | Popularity @10 |
|---|---:|---:|---:|---:|
| Precision | 0 | .20 | 0 | .10 |
| Recall | 0 | 1 | 0 | 1 |
| NDCG | 0 | 1 | 0 | 1 |
| Coverage | .0251 | .0251 | .0503 | .0503 |
| Category diversity | .6000 | .4000 | .6444 | .7111 |

Next, retain a cutoff snapshot and collect a separate outcome window from consenting users. Record sample size and selection bias when interpreting results. Admin integration is deferred until real measured results are available.
