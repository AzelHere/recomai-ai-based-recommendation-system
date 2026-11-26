# Recommendation generation benchmarks

## Run

From the repository root, use Python 3.10+, a running Docker Desktop with Linux containers and sufficient disk for one new named MySQL volume per run. The application and database use their existing images, startup checks and Flyway migrations.

```powershell
python -B scripts/test_benchmark.py
python scripts/benchmark.py small medium
python scripts/benchmark.py large stress
```

To vary load, for example, append `--workers 4 --warmup 12 --iterations 200`. Start with small and medium. Check disk space, memory and CPU load before scaling up. For comparable runs keep code, Docker/Java/MySQL versions, host load, environment settings, worker count and seed constant. Run multiple independent trials before making capacity decisions.

Each invocation generates its data before warm-up. Each scenario gets a random Compose project name, random credentials, loopback-only dynamically assigned ports, an empty database named for that project, and a new project-owned volume. There is no target URL or external database option. Generation checks the database identity, empty user table and starter catalog size. The runner stops its own containers and network with Compose down in a finally block and retains every volume. After an interruption, use the exact generated project name with `docker compose -p <project> down`. Raw JSON results are in `backend/target/benchmarks`.

## Measured baseline — 21 November 2026

One run per size, 10:11–10:14 UTC. Each used 5 warm-ups, 40 measured top-ten requests and concurrency 2. Docker reported x86_64, 14 logical CPUs and 7.48 GiB RAM on Windows (Intel family 6 model 197). Java was Temurin 21.0.13.1, MySQL 8.4.11, with a 128 MiB InnoDB buffer pool and 151 maximum connections.

| Scenario | Avg ms | P50 ms | P95 ms | P99 ms | Successful requests/s | Errors | Backend / MySQL memory after run (MiB) |
|---|---:|---:|---:|---:|---:|---:|---:|
| Small | 46.31 | 43.46 | 67.31 | 76.30 | 41.91 | 0/40 | 239.6 / 468.4 |
| Medium | 69.00 | 66.40 | 87.01 | 90.22 | 28.88 | 0/40 | 294 / 475 |
| Large | 149.48 | 145.47 | 181.84 | 183.56 | 13.37 | 0/40 | 257 / 505.3 |
| Stress | 254.39 | 252.32 | 283.32 | 287.71 | 7.86 | 0/40 | 225.4 / 543.4 |

| Scenario | Preliminary candidate SQL avg ms | Rows examined per call (approx.) | Collaborative SQL avg ms |
|---|---:|---:|---:|
| Small | 3.471 | 2,744 | 1.508 |
| Medium | 12.485 | 21,887 | 6.310 |
| Large | 56.028 | 105,271 | 28.216 |
| Stress | 109.983 | 219,925 | 56.846 |

The largest SQL cost at each size was the preliminary query. It computes multiple per-item aggregates and sorts eligible resources before applying LIMIT. In stress, popularity added 29.54 ms per call. Its EXPLAIN showed a covering range scan on ix_interactions_user_type_recent_item and a filesort. The medium plan chose ix_interactions_type_recent_item_user instead. Ten per-item inserts and reads occur per generated batch; individual inserts averaged 0.256 ms at medium size. The TF-IDF metadata query averaged 5.978 ms at medium and 4.680 ms at stress. Those figures exclude Java vector construction. Statement sampling does not attribute JVM CPU, network or GC separately.

The cap bounds downstream vector construction and scoring. In the large and stress diagnostic, the first worker had 3,996 and 8,996 eligible resources outside the shortlist; 94 and 1,215 respectively had a positive topic or category match. Preliminary ordering excludes metadata cosine, collaborative evidence and ratings. The diagnostic cannot establish which omitted resources would outrank a selected result. Small and medium fit within the existing cap.

The [candidate query experiment](candidate-query-experiment.md) tested deferring item ratings until after selection. Both stress trials reproduced the same slowdown, and the production query was retained. The experiment also evaluates larger shortlists against the original candidate IDs.

## Workload

- Small, medium, large and stress use 100/200, 1,000/1,000, 5,000/5,000 and 10,000/10,000 users/items respectively. The resource count includes the 200 seeded resources. Additional synthetic articles get a category, topic, description and quality value.
- Python's local PRNG uses seed 8173. Each synthetic user receives one category and topic preference, eight distinct item interactions, one rating and one historical recommendation. The interaction mix includes recent exposures, likes, saves, completion and a rejection. Relative event ages start at generation time. Login secrets, wall-clock timestamps, project names and elapsed times vary. Data is sparse, with no organically formed communities or long-term interest drift.
- Each worker registers once and keeps its own session. Timed calls POST to the actual recommendation generation endpoint and require ten distinct resource IDs. This includes authentication, user locking, candidate SQL, content vectors, ranking, writing a batch and returning JSON. Opening previously generated picks is a different read path. The two default workers generate concurrently for two separate users. The benchmark does not simulate 10,000 concurrent sessions.
- After five warm-up requests, the default run measures forty requests. Mean and nearest-rank quantiles use successful request durations. Throughput divides successes by total elapsed wall time. Error rate uses all attempted measured requests. P99 with forty samples is the observed maximum.
- MySQL Performance Schema statement digest deltas cover the measured interval. Query times are server execution times, distinct from HTTP latency. Docker stats provides end-of-run container memory including native allocations; it does not give peak JVM heap. The report includes an EXPLAIN for the popularity query and, for the first worker, a diagnostic count of eligible items beyond the production shortlist and how many match a category or topic. This diagnostic runs after measurement. A positive match is a ranking signal and does not establish user satisfaction.
