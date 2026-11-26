# Recom AI technical quality verification

Measured 26 November 2026 against the existing `master` baseline `09335f8edf7f9f935bed545288c91fbb61412753`. This phase repairs supported behavior and measures its limits. It does not certify production capacity or complete security/accessibility conformance.

| Check | Baseline | Reviewed result |
|---|---:|---:|
| Maven tests | 167 passing | 195 passing, no failures/skips |
| SpotBugs priority 2 findings | 175 | 112; 63 resolved, no new suppressions |
| Priority 1 findings | 0 | 0 |
| Maven-only line / branch coverage | 41.45% / 34.20% | 41.77% / 34.72% |
| Maven plus instrumented real-database browser coverage | Not measured | 76.79% line / 55.32% branch; scope differs from Maven-only |
| Runtime image package/advisory matches | 149 medium, 12 low | 63 medium, 12 low; zero critical/high |
| Unique image advisory IDs | 67 | 38 |
| OSV resolved Maven / npm findings | 0 / 0 | 0 / 0 |
| Catalog / articles / held editions | 2,365 / 505 / 105 | Preserved; no speculative imports or merges |
| Admin editor reload payload | 2,582,289 bytes | 615 bytes for the measured record |

The meaningful corrections are exception-path JDBC statement cleanup in twelve writers, defensive snapshots for collection/byte-array records, rejection of IPv6 transition/special-purpose source addresses, an authorized single-record administrator reload with a stale-workspace guard, and muted-text/button contrast repairs. Administrator ingestion now exposes stored link health by source. Ranking weights, applied migrations, technology stack and graphite design are preserved.

The complete [175-row static-analysis register](spotbugs-resolution-register.csv) records warning ID, priority, category, class, method, field, baseline location, root cause, impact and final disposition. Remaining findings are 91 trusted Spring dependency references, 17 SQL/validation contract warnings, two SQL newline warnings, one intentional source-pacing lock, and one fail-fast constructor lifecycle warning. They remain visible in fresh SpotBugs output. Acceptance is scoped to the current contracts, not a blanket exemption for future code.

New checks cover statement ownership and suppressed cleanup failures; record mutation boundaries and null validation; IPv4/IPv6 source-address boundaries; hand-calculated TF-IDF/cosine; graded retrieval metrics; administrator authorization for paused records; nine automated accessibility surfaces; and delayed search responses. The test-only sign-in fixture honors one bounded `Retry-After` response when shared-IP suites reach the real production limit. Production throttling is unchanged.

See [coverage and reproduction](coverage.md), [security](security.md), [current-catalog evaluation](recommendations.md), [measured performance](performance.md), [catalog and links](catalog.md), and [verification evidence](verification.json). Historical publication measurements remain in [the earlier audit](../code-quality/README.md).

Residual limits include branch coverage below its 60% goal, process-local session/rate-limit/cache state, a large frontend orchestration module, substring queries, provider concentration, uncertain metadata, editorial rather than independent human relevance judgments, unpatched medium/low OS advisories, and local short-duration load measurements. No public application deployment was performed.
