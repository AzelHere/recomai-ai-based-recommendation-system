# Recom AI audit report

## Code quality

186 production Java files were scanned structurally and with SpotBugs, with focused manual tracing of security, SQL, transactions, cache, ranking and journeys. First-party frontend scope: one HTML, five CSS and ten JavaScript files; all ten JavaScript syntax checks passed. Two meaningful policy tests cover ranker bounds and null ingestion rows. JavaDoc clarifies six critical areas. Remaining static warnings and maintenance debts are disclosed in [the full report](README.md): 175 priority-2 warnings, no priority-1 warnings. No exhaustive defect-free certification is claimed.

## Testing

- Maven: 167 passed; zero failed/skipped.
- Playwright: 29 passed, zero failed, three optional tests skipped with one worker; the same counts with two workers.
- Explicit gallery: one passed; 18 genuine 1440×900 screenshots, visually inspected.
- JVM coverage: line 41.45%, branch 34.20%, instruction 34.34%, method 41.50%, class 58.25%. Browser execution is excluded from these measured JVM counters.
- Independent clean clone of source commit `796a9c3`: npm ci, zero npm vulnerabilities, 167 Maven tests, Docker build, fresh MySQL initialization, V1–V18 and populated browser walkthrough passed. Included datasets reproduced 2,365 resources and 505 articles/readings without the private development database.
- CI is configured in the repository; only an actual remote run can establish its status. See GitHub Actions and the final publication verification.

## Repository organization

`frontend/` contains source copied into the packaged Spring Boot static resources. `backend/src/main/java/` contains controllers, services, DAOs, models, web security, catalog ingestion and `recommendation/`. `backend/src/main/resources/db/migration/` contains Flyway migrations. Java tests are in `backend/src/test/java/`, browser tests in `e2e/`, permitted educational metadata in `data/`, documentation in `docs/` and CI in `.github/workflows/`.

The [README](../../README.md) supplies direct source navigation and reproducible setup. [Architecture](../architecture/README.md), 11 additional Mermaid workflows, the core [ER diagram](../database/README.md), precise API/schema/algorithm references and the [18-image gallery](../screenshots/README.md) describe actual components. The repository banner uses the existing graphite/copper identity and official name Recom AI.

## Safety and publication

The existing public repository is `AzelHere/recomai-ai-based-recommendation-system`, branch `master`. Its remote was verified and corrected from the former username URL; four original commits and their authorship are preserved. No new repository, rename, force push or unrelated change overwrite is authorized or performed. The final remote SHA and CI status are verified after the push; a local commit alone is not publication.

All 18 applied migration files match audit-start hashes. Existing development data and Docker volumes are preserved; production hosting remains outside this task. Credential-signature and exact-local-secret scans found no historical or proposed matches within their declared scope. Real `.env`, raw/restricted records, private fingerprints, caches, build outputs, academic presentation generators and redundant ZIPs are excluded. Existing academic files removed from the proposed Git tree remain locally and in historical commits. Educational attribution/noncommercial/share-alike conditions and existing asset notices are retained; no unconfirmed software license or team contributions are invented.
