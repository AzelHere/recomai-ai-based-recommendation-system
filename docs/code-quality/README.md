# Recom AI code quality and publication audit

Recorded on 26 November 2026. This is a bounded source, bytecode and regression audit, not a guarantee that every defect has been found.

## Scope and initial state

The initial local/remote HEAD was `34094b6f8c4c79847d84d14ed010d8515ffc7cbd`, branch `master`. GitHub confirmed the existing public repository belongs to `AzelHere`; the former username remote was updated to the canonical URL. No divergence was found. Four existing commits and genuine authorship are retained.

186 production Java sources were structurally inspected and analyzed with SpotBugs. Focused manual tracing covered controllers/session checks, owner-scoped services/DAOs, import transactions, TF-IDF/hybrid ranking, cache publication and learning journeys. This is not a claim of a separate line-by-line human review of every file. [Source inventory](source-inventory.json). All ten first-party JavaScript files passed Node syntax checking; five CSS files and the HTML were inspected for layout/semantics and exercised through browser workflows. Vendored Three.js is covered by its upstream attribution rather than rewritten. [Frontend checks](frontend-checks.json).

The working project contained generated ZIPs, raw downloads, scanner caches, private preservation fingerprints and repeated academic artifact snapshots. These remain local/ignored and are excluded from the proposed Git submission. No database, Docker volume or raw user records were deleted. Existing migration hashes are preserved. [History safety scan](history-safety.json).

## Findings and corrections

| Severity | Finding | Disposition |
|---|---|---|
| Medium | Direct ranker calls accepted exploration outside 0–100, producing invalid allocation shares | Added ranker-level validation and boundary tests; HTTP validation retained |
| Low | Null ingestion rows relied on an indirect validation exception; analyzer could not establish non-null flow | Added explicit REJECT row and regression test; required category/type before provider-specific checks |
| Low | Dead local variable in category deactivation | Removed assignment while retaining the existence validation call |
| Low | Outdated user-facing brand and skeletal Maven project description | Website/email copy, banner and repository documentation use Recom AI; internal identifiers retained |
| Low | Unix Maven wrapper skipped an unterminated final distribution URL line | Added the required final newline and explicit wrapper line-ending attributes; verified under Linux |
| Low | Query-plan and recovery evidence writers assumed a pre-existing ignored directory | Each helper now creates its own output directory; all database/assertion checks retained |
| Informational | Missing measured coverage and CI entry point | Added JaCoCo without exclusions and an isolated GitHub Actions workflow |
| Informational | Non-obvious cache/session/transaction assumptions | Added concise JavaDoc to scorer, ranker, recommendation service, SessionAccess, ingestion and journey services |

No critical/high defect was confirmed in this bounded review. This does not mean all static findings are resolved or that production deployment is cleared.

## Static analysis

SpotBugs Maven plugin **4.10.5.0**, command documented in [testing](../testing/README.md), completed against all compiled production classes. Initial result: 177 warnings (one priority 1, 176 priority 2). After explicit null flow and dead-store cleanup: **175 priority-2 warnings, zero priority-1 warnings**. Tool priorities are not security severity ratings. [Every remaining warning](spotbugs.json).

| Remaining group | Count | Triage and limitations |
|---|---:|---|
| EI_EXPOSE_REP / EI_EXPOSE_REP2 | 141 | Mostly injected Spring collaborators and DTO collection references; do not blanket-copy framework dependencies. Individual collection immutability remains follow-up work |
| Nullable database return contracts | 17 | Spring JDBC query results/unboxing and map assumptions; further explicit contracts would improve analyzer confidence |
| Statement cleanup on exceptional callback paths | 12 | Spring closes successfully returned statements; parameter binding failure before returning a statement still merits follow-up cleanup review |
| Sleep under source-fetch lock | 1 | Deliberate sequential operator rate limiting; acquisition never runs on request/startup paths |
| Format-string newline portability | 2 | CLI output portability; low impact |
| Ternary unboxing/coercion | 1 | Numeric conversion clarity; low impact |
| Constructor-throw warning | 1 | Fail-fast configuration validation; no finalizer is implemented |

The native Windows SpotBugs attempt failed with `No files to analyze could be opened` in the spaced workspace path. The container invocation succeeded. Maven compiler diagnostics and JavaScript syntax checks complement it. Checkstyle/PMD were evaluated as style/overlapping rule suites and were not added merely to generate extra reports. No mass reformat or architecture rewrite was performed.

## Measured JVM coverage

JaCoCo **0.8.14** supports the Java 21 build ([upstream release notes](https://www.jacoco.org/jacoco/trunk/doc/changes.html)). Command: `mvnw -B -f backend/pom.xml clean verify`. No package exclusions or invented thresholds. These numbers describe Maven JVM tests only; browser tests run in another JVM and are not included.

| Metric | Covered / total | Percent |
|---|---:|---:|
| Line | 1447 / 3491 | 41.45% |
| Branch | 827 / 2418 | 34.2% |
| Instruction | 9465 / 27563 | 34.34% |
| Method | 381 / 918 | 41.5% |
| Class | 120 / 206 | 58.25% |

### Important package coverage

| Package | Line | Branch |
|---|---:|---:|
| recommendation | 95.06% | 75.68% |
| dao | 13.51% | 10.09% |
| service | 52.05% | 39.55% |
| evaluation | 16.1% | 9.22% |
| web | 83.01% | 70.0% |
| controller | 29.66% | 14.81% |
| model | 80.0% | 33.33% |
| catalog | 24.72% | 26.31% |
| recommendationsystem | 33.33% | None% |
| exception | 88.0% | None% |

Coverage gaps are material in catalog operators, DAOs and services exercised mainly through real-database browser tests. This is not high whole-application JVM coverage. Two targeted tests were added for actual boundary behavior, not to inflate percentages. [Exact counters](coverage.json); HTML/XML regenerate under `backend/target/site/jacoco/`.

## Regression and publication evidence

Maven: **167 passed, zero failed, zero skipped**. Final worker results, clean-clone verification and remote CI status are recorded in [publication results](publication-results.json) as those checks complete. The optional historical visual/render tests and explicit publication gallery are skipped in normal runs; skipped tests are not counted as passes.

The read-only development catalog checks verified 2,365 resources, 505 ARTICLE rows, combined filters, pagination, provenance and health. Preservation fingerprints verified original data and the narrowly allowed earlier article classification changes. No migration was edited by this audit. [Public catalog checks](catalog-verification.json) · [preservation result](data-preservation.json).

Browser regression includes authentication/recovery, onboarding, feedback, recommendations/explanations, independent search/type/topic/provider filters, owner libraries/collections, journeys, progress/activity, settings, admin authorization, optimistic revisions, import idempotency, source review and migration/backup recovery in isolated databases. Generated test data never enters the private development database.

## Maintainability follow-up

`frontend/js/app.js` remains a large orchestration module and the graphite stylesheet layers compatibility overrides over the base styles. These are low-severity maintenance debts, not grounds for a redesign during publication. `LearningJourneyService` combines owner-scoped SQL and workflow logic; extracting DAO methods can be considered when it materially improves change safety. Some long SQL methods are intentionally kept near their signal definitions. No unused-code claim is made merely from textual searches, because DOM event wiring and framework injection are indirect.

## Publication limits

The existing repository remains public; no source-code license was assigned. Licensed educational metadata retains attribution and noncommercial/share-alike restrictions. Raw learner files, caches, `.env`, secrets, database dumps, generated archives and academic slide/viva generators are excluded. No force push, history rewrite, repository rename or public app deployment is part of this task. Production operational/security clearance remains separate.
