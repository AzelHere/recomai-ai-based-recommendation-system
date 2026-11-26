# Deferring item ratings until after candidate retrieval

The production query aggregates feedback from other accounts by item, joins those averages to all eligible resources, sorts and retains up to 1,000. The personal category average is calculated separately. Neither average contributes to the preliminary ORDER BY or filters. Each rating join has at most one row per key. Both averages matter later: rating affinity contributes points and can affect a bucket and its explanation.

The experimental query keeps the user's category average and moves only the other-user item average. A shortlist CTE contains the original eligibility rules, preliminary ordering, item-ID tie-break and LIMIT. An outer correlated AVG reads feedback for each shortlisted ID, excludes the current user and yields SQL NULL for an unrated item. The outer query explicitly restores the original order. The experimental Java class and Docker image are built from a temporary source copy; the application checkout's DAO remains the reference implementation.

## Reproduction

Run in the repository root with Python 3.10+ and Docker Desktop. Use unique trial numbers to retain earlier measurements. Each command creates fresh isolated Compose projects and retains their volumes. Workloads use the existing benchmark generator, seed 8173, five warmups and forty measured requests with two authenticated workers. Run them sequentially on an otherwise idle host.

```powershell
python -B scripts/candidate_experiment.py original 1 small medium large stress
python -B scripts/candidate_experiment.py deferred 1 small medium large stress
python -B scripts/candidate_experiment.py deferred 2 stress
python -B scripts/candidate_experiment.py original 2 stress
python -B scripts/test_candidate_experiment.py compare
python -B scripts/candidate_experiment.py cap2000 1 stress
python -B scripts/candidate_experiment.py cap10000 1 stress
python -B scripts/report_candidate_experiment.py
```

New artifacts are saved under ignored `logs/candidate-experiment/<trial>-<variant>-<size>`, which survives Maven clean. Each records the SQL execution plan, raw ordered preliminary rows (compressed TSV), persisted API draft fields (JSON), and the HTTP and MySQL measurements. The comparison requires exact equality of every projected field including preliminary ordering inputs and both rating averages. It compares final order, scores, contribution maps, bucket, reason type and explanation text. Database-generated batch/recommendation IDs and generation timestamps vary by run and are excluded. Cases include two distinct accounts, one rating, no ratings or interactions, a dense mix of ratings, conflicting ratings from another account, likes, hides and dislikes, exploration values 0/50/100, and an inactive catalog. After timing, an isolated missing-feedback-table probe verifies an HTTP 500 rather than a successfully persisted partial feed.

The cap experiments use the original rating query throughout. They compare expanded top-ten IDs against the entire original 1,000-item candidate set for each profile. Saved SQL times measure the preliminary query; full API times also include bounded metadata fetches, TF-IDF computation, final scoring and batch persistence. These synthetic fixtures measure retrieval behavior and runtime. User-rated recommendation quality requires a separate held-out evaluation.

## Adoption decision and measurements

Retain the production query. All 30 paired profile comparisons passed exact equality, including null ratings, reconstructed preliminary scores, item-ID tie breaks, final scores and explanations. At stress size the original plan materialized 6,313 rated-item groups once. The experimental dependent subquery scanned 10,000 feedback rows for each of 1,000 candidates. Measured candidate SQL time increased roughly seven- to eightfold in both trial orders. Smaller HTTP timing differences, especially at small size, should be interpreted with the tail latencies and short sample size in mind.

All times below are milliseconds. Rows/call are statement digest deltas. Memory is backend/MySQL container usage sampled after the timed interval. All workloads are synthetic.

| Workload | Trial | Query | Mean | P50 | P95 | P99 | Candidate SQL | Rows/call | Container memory | Error rate |
|---|---|---|---:|---:|---:|---:|---:|---:|---|---:|
| small | 1 | deferred | 47.05 | 41.11 | 74.85 | 76.97 | 5.468 | 20566 | 222.3MiB/472.7MiB | 0.0 |
| small | 1 | original | 50.01 | 50.09 | 62.63 | 73.55 | 3.745 | 2744 | 231MiB/469.8MiB | 0.0 |
| medium | 1 | deferred | 147.9 | 147.52 | 169.01 | 172.17 | 86.731 | 1000962 | 227.1MiB/479.8MiB | 0.0 |
| medium | 1 | original | 83.74 | 82.28 | 105.72 | 120.1 | 14.856 | 21888 | 254.5MiB/477.5MiB | 0.0 |
| large | 1 | deferred | 519.42 | 512.25 | 557.88 | 585.66 | 424.554 | 5016860 | 225MiB/513.8MiB | 0.0 |
| large | 1 | original | 154.38 | 148.61 | 189.61 | 212.41 | 57.229 | 105271 | 251MiB/505.4MiB | 0.0 |
| stress | 1 | cap10000 | 410.51 | 402.59 | 488.85 | 496.11 | 124.84 | 227516 | 434.2MiB/559.3MiB | 0.0 |
| stress | 1 | cap2000 | 264.57 | 260.83 | 302.58 | 317.68 | 108.675 | 220926 | 280.2MiB/540.9MiB | 0.0 |
| stress | 1 | deferred | 992.52 | 993.24 | 1048.69 | 1096.38 | 837.31 | 10026342 | 240MiB/552.1MiB | 0.0 |
| stress | 1 | original | 267.73 | 267.54 | 309.36 | 330.21 | 113.189 | 219926 | 230.1MiB/541.3MiB | 0.0 |
| stress | 2 | deferred | 993.84 | 984.09 | 1112.36 | 1149.63 | 848.532 | 10026342 | 239.6MiB/552.6MiB | 0.0 |
| stress | 2 | original | 246.19 | 242.12 | 279.43 | 281.16 | 105.978 | 219926 | 221.1MiB/542MiB | 0.0 |

At stress size the 1,000 cap omitted 8,996 otherwise eligible items for the first balanced user, including 1,215 with positive category or topic evidence. With the 2,000 cap, none of five nonempty profiles gained a top-ten item outside its original shortlist. With the 10,000 cap, counts were 3 (balanced), 2 (second account), 0 (sparse focused), 6 (dense adventurous), and 2 (second account after the first account changed feedback). This establishes that preliminary selection can miss items favored by the full pipeline. It does not establish that users prefer those items. Changing the pool also changes the corpus used for TF-IDF. The cap trials were single observations, and the 10,000 run was completed in a later session. Keep the existing cap while investigating quality and cost together.

## Grouped shortlist feedback experiment (2026-11-23)

The second experimental query first applies the unchanged preliminary ordering and 1,000-item limit in a `shortlist` CTE. `shortlisted_item_ratings` joins feedback to those IDs, excludes the active user's feedback, and computes one `AVG` per item. The outer query left joins the averages and restores the same score and item-ID order. It retains SQL NULL for items with no other-user rating. No Java recommendation or production SQL code changed.

MySQL 8.4 `EXPLAIN ANALYZE` at stress size showed `shortlist` materialized once (1,000 rows). The grouped rating CTE materialized once (619 rated shortlisted items), scanning 10,000 feedback records and probing the shortlist by item ID. The original materialized 6,313 rating groups from one indexed feedback scan before joining eligible items. The earlier correlated version instead scanned feedback once per shortlisted item. The grouped join reduced statement rows examined from 219,926 to 32,592 at stress size, but its extra shortlist materialization, join and outer sort still cost time. These row counts include the whole candidate statement, not just feedback.

Each grouped trial passed six exact profile comparisons against the corresponding original trial: ordered candidate rows and averages, preliminary score and tie break, ordered final recommendations, contribution maps, reasons and explanations. Cases cover two accounts, sparse/no feedback, conflicting ratings, hides/dislikes, focused/adventurous settings and an inactive catalog. The diagnostic SQL-failure probe also completed. All measured API requests had zero errors. Trial 1 ran original before grouped; trial 3 repeated all four workloads in that order; trial 4 ran grouped before original for stress. Seed 8173, dataset, five warmups, forty timed requests and two workers were held fixed. Times are milliseconds; memory is backend/MySQL after timing.

| Workload | Trial | Strategy | Mean | P50 | P95 | P99 | Candidate SQL | Rows/call | Memory | Errors |
|---|---:|---|---:|---:|---:|---:|---:|---:|---|---:|
| Small | 1 | Original | 50.01 | 50.09 | 62.63 | 73.55 | 3.745 | 2,744 | 231/469.8 MiB | 0% |
| Small | 1 | Grouped | 55.83 | 49.04 | 83.84 | 121.45 | 5.460 | 1,044 | 259.3/478.5 MiB | 0% |
| Small | 3 | Original | 50.78 | 50.32 | 70.76 | 79.42 | 3.931 | 2,744 | 248.6/469.1 MiB | 0% |
| Small | 3 | Grouped | 55.59 | 52.40 | 85.46 | 94.28 | 5.717 | 1,044 | 264.7/475.7 MiB | 0% |
| Medium | 1 | Original | 83.74 | 82.28 | 105.72 | 120.10 | 14.856 | 21,888 | 254.5/477.5 MiB | 0% |
| Medium | 1 | Grouped | 77.20 | 75.56 | 93.42 | 101.41 | 15.433 | 5,594 | 293.2/483.5 MiB | 0% |
| Medium | 3 | Original | 76.84 | 76.08 | 102.13 | 108.74 | 14.264 | 21,888 | 282/477.9 MiB | 0% |
| Medium | 3 | Grouped | 88.05 | 83.06 | 135.30 | 144.33 | 17.823 | 5,594 | 294.3/484.5 MiB | 0% |
| Large | 1 | Original | 154.38 | 148.61 | 189.61 | 212.41 | 57.229 | 105,271 | 251/505.4 MiB | 0% |
| Large | 1 | Grouped | 165.27 | 162.85 | 185.18 | 203.55 | 60.464 | 17,602 | 268.3/507.2 MiB | 0% |
| Large | 3 | Original | 184.92 | 184.95 | 207.75 | 212.67 | 63.099 | 105,271 | 261.5/502.5 MiB | 0% |
| Large | 3 | Grouped | 150.49 | 150.53 | 166.49 | 173.35 | 51.992 | 17,602 | 285.6/507.6 MiB | 0% |
| Stress | 1 | Original | 267.73 | 267.54 | 309.36 | 330.21 | 113.189 | 219,926 | 230.1/541.3 MiB | 0% |
| Stress | 1 | Grouped | 233.31 | 231.34 | 264.44 | 275.12 | 99.889 | 32,592 | 258.9/539.4 MiB | 0% |
| Stress | 3 | Original | 274.61 | 275.57 | 301.42 | 320.76 | 111.823 | 219,926 | 272.7/539.4 MiB | 0% |
| Stress | 3 | Grouped | 236.75 | 231.53 | 281.88 | 297.03 | 103.611 | 32,592 | 266.5/539.6 MiB | 0% |
| Stress | 4 | Grouped | 271.84 | 275.96 | 288.77 | 302.90 | 108.804 | 32,592 | 259.8/537.6 MiB | 0% |
| Stress | 4 | Original | 257.39 | 252.49 | 276.69 | 292.15 | 115.263 | 219,926 | 253.2/537.2 MiB | 0% |

**Decision: reject production adoption.** Grouped SQL reduces examined rows but the API gain does not repeat in reversed order. Small workload latency and candidate SQL latency regress in both pairs; medium and large results vary by trial. Memory is also generally higher. The 1,000-item production cap stays unchanged. A larger pool might amortize the grouped approach, but that requires a separate quality and performance study, because expanding the cap also changes TF-IDF inputs and recommendation results.

The Maven `clean` verification after these trials cleared the old `backend/target/candidate-experiment` artifacts. To preserve future raw rows, plans and metrics across clean builds, the experiment harness now writes to ignored `logs/candidate-experiment`. The measured values and comparison outcomes above were recorded before cleanup. Reproduce them with the same commands above using fresh trial numbers; never run `mvn clean` while an experiment is active.
