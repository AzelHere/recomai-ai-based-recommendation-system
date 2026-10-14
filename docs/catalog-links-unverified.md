# Catalog link audit — 2026-10-10

## Result

The checker read the current runtime catalog from the isolated application after Flyway V8 and checked 200 unique URLs drawn from the runtime catalog and seed resources. It issued HEAD requests with GET fallback, followed redirects, and used a 7-second timeout with 30 concurrent checks.

- 179 URLs returned HTTP 2xx/3xx.
- 0 URLs returned HTTP 404 after the V8 corrections.
- 21 URLs still need human review: 15 returned HTTP 403, one returned HTTP 429, and five timed out or failed before a status could be confirmed. A 403/429 or timeout is inconclusive; it does not prove the destination is invalid.
- The 13 URLs with confirmed 404 responses were replaced by publisher landing pages in V8 and the curated seed file. The new catalog was rechecked and has 200 active items and 25 editor picks.

Run `npm run check:catalog-links` to repeat the check. Set `CATALOG_API_URL` to the intended application's `/api/items` endpoint to include its current runtime catalog. Link status can change over time; 2xx/3xx responses confirm only that the URL responded during this check.

## URLs needing review

### HTTP 403 — automated request denied

- https://linuxjourney.com/
- https://www.score.org/resource/business-plan-template-startup-business
- https://www.careeronestop.org/Toolkit/Skills/skills-matcher.aspx
- https://www.futurelearn.com/subjects/business-and-management-courses/career-development
- https://open.lib.umn.edu/communication/
- https://www.open.edu/openlearn/money-business/leadership-management/effective-communication/content-section-overview
- https://leetcode.com/explore/interview/card/top-interview-questions-easy/
- https://www.heart.org/en/healthy-living/fitness
- https://www.moma.org/learn/moma_learning/
- https://writingcooperative.com/
- https://www.loc.gov/collections/chronicling-america/
- https://learninglab.si.edu/
- https://www.exploratorium.edu/learn
- https://www.careeronestop.org/
- https://www.canva.com/designschool/

### HTTP 429 — rate limited

- https://www.metmuseum.org/toah/

### Timeout or fetch error — status unconfirmed

- https://www.myplate.gov/myplate-plan
- https://www.nhlbi.nih.gov/health/sleep
- https://inkscape.org/learn/tutorials/
- https://typography.com/
- https://rps.org/learning/
