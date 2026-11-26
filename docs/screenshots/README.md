# Recom AI screenshot walkthrough

These are genuine 1440×900 browser captures from the final local application image, using a newly generated isolated demonstration account and database. No private development account or database appears. Reduced motion is enabled. The 200 curated resources and permitted metadata are loaded through the same application ingestion service. [Capture manifest and hashes](manifest.json).

Reproduce using `PUBLICATION_CAPTURE=1` with `npm run test:e2e -- e2e/publication-gallery.spec.js`. This is an explicit capture-only test, skipped in ordinary regression runs. [Frontend](../../frontend/) · [controllers](../../backend/src/main/java/com/guvi/recommendationsystem/controller/) · [services](../../backend/src/main/java/com/guvi/recommendationsystem/service/).

## Landing page

Choose a temporary goal or explore resources. The public page explains the actual recommendation workflow.

![Recom AI — Landing page](01-landing-page.png)

## Authentication

Register or sign in. This captured form contains no real credentials.

![Recom AI — Authentication](02-authentication.png)

## Learning goals

Choose an onboarding purpose. The selected goal seeds recommendations.

![Recom AI — Learning goals](03-learning-goals.png)

## Interest selection

Choose a primary area, then optional topics. Preferences remain editable.

![Recom AI — Interest selection](04-interest-selection.png)

## For You

Review real catalog picks generated for a demonstration Java learner.

![Recom AI — For You](05-personalized-recommendations.png)

## Explanation

Open View details. The application displays the stored reason for the selected recommendation.

![Recom AI — Explanation](06-recommendation-explanation.png)

## Explore

Browse paginated resources from the imported licensed catalog.

![Recom AI — Explore](07-explore-catalog.png)

## Articles and readings

Select ARTICLE. Clearly labeled textbook sections are included alongside individual articles.

![Recom AI — Articles and readings](08-articles-filter.png)

## Combined filters

Combine ARTICLE, Java query and Microsoft Learn provider. Independent filters intersect.

![Recom AI — Combined filters](09-search-and-filters.png)

## Saved resources

Save resources and return to a personal library.

![Recom AI — Saved resources](10-saved-resources.png)

## Collections

Filter the library to a named reading shelf. Membership can overlap other collections.

![Recom AI — Collections](11-collections.png)

## Learning journey

Generate a persisted Java learning suggestion with explicit step state.

![Recom AI — Learning journey](12-learning-journey.png)

## Progress

Review explicit completion and progress events. Clicks are not completion.

![Recom AI — Progress](13-learning-progress.png)

## Activity

Inspect the owner-scoped recorded event history.

![Recom AI — Activity](14-activity-history.png)

## Account settings

Review supported account controls and links to profile, recovery and deletion.

![Recom AI — Account settings](15-profile-settings.png)

## Administrator dashboard

An isolated demonstration administrator sees current database aggregates, not invented growth metrics.

![Recom AI — Administrator dashboard](16-admin-dashboard.png)

## Catalog management

Search, filter and edit actual catalog resources through authorized admin controls.

![Recom AI — Catalog management](17-catalog-management.png)

## Resource provenance

Open a resource and inspect its source attribution, license and original link.

![Recom AI — Resource provenance](18-resource-provenance.png)
