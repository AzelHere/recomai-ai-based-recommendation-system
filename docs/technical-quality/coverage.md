# Recom AI coverage and reproducibility

JaCoCo 0.8.14 measures all 207 production classes, including catalog operators and evaluation code; no package exclusions were added. Maven runs 195 tests. Its own coverage is 1,481/3,546 lines (41.77%) and 861/2,480 branches (34.72%). The prior Maven-only result was 1,447/3,491 lines (41.45%) and 827/2,418 branches (34.20%). Denominators changed with actual production repairs.

The combined report merges that execution data with the application JVMs exercised through isolated Playwright tests using real MySQL, Flyway and HTTP. This is integration coverage, not an increase in unit-only coverage. The JaCoCo agent writes a local file on graceful shutdown; it opens no network listener and is absent from the normal application image. Class IDs must match the compiled source/image; do not merge data from earlier source revisions. Failed test attempts are retained as diagnostics but excluded from the final merged report.

Combined coverage is 2,723/3,546 lines (76.79%) and 1,372/2,480 branches (55.32%). The 75% line goal is met by combined execution; the 60% branch goal is not met. Remaining branches include offline acquisition/evaluation operators and exceptional database/network paths. Artificially excluding them, asserting implementation details or weakening validation would misrepresent quality. Automated browser coverage does not replace focused exception-path tests.

```sh
sh mvnw -B -f backend/pom.xml clean verify
docker build -t recomai-technical:local .
docker build -f Dockerfile.coverage --build-arg APP_IMAGE=recomai-technical:local -t recomai-technical-coverage:local .
# POSIX shell; PowerShell uses $env:NAME='value'
E2E_EXISTING_IMAGE=recomai-technical-coverage:local E2E_COVERAGE_DIR=target/technical-coverage npm run test:e2e -- --workers=1
```

Use the JaCoCo 0.8.14 CLI `merge` on `backend/target/jacoco.exec` and the resulting successful-run `.exec` files. Run CLI `report` with `--classfiles backend/target/classes --sourcefiles backend/src/main/java`. HTML/XML/CSV in the evidence ZIP retain exact counters and package breakdowns. Maven-only HTML remains separately labeled. A clean rebuild requires new compatible integration execution data.

The three normal-suite skips are explicitly gated evidence collectors: `phase4-rendering.spec.js` (`PHASE4_RENDER=final`), `phase4-visual.spec.js` (`PHASE4_CAPTURE=final`) and `publication-gallery.spec.js` (`PUBLICATION_CAPTURE=1`). All three were separately executed and passed; the visual case checks 1366, 1440, 1536, 1920 and 2560 desktop widths. The gallery refreshes 18 genuine captures from an isolated demonstration account.

```sh
PHASE4_RENDER=final PHASE4_CAPTURE=final PUBLICATION_CAPTURE=1 npm run test:e2e -- e2e/phase4-rendering.spec.js e2e/phase4-visual.spec.js e2e/publication-gallery.spec.js --workers=1
```

Automated WCAG A/AA rule checks pass on landing, registration, recommendations, catalog, saved, progress, profile, settings and admin. This is an automated rule subset, not full WCAG conformance. Existing workflow tests exercise keyboard focus, dialogs, paging, failures, independent filters and desktop layout. Three.js measurements recorded zero request-animation-frame callbacks during two-second reduced-motion and hidden-landing windows; context-loss fallback passed. Global callback counts are not a claim of display FPS or memory-leak freedom.
