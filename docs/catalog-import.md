# Catalog resources and CSV import

The catalog is a learning and career discovery hub across Technology, Business & Finance, Learning & Career, Health & Fitness, and Creative & Lifestyle. A clean application startup adds 175 curated public resource links to the 25 records established by Flyway V3. The startup loader checks each URL before inserting, adds the three topic tags stored in the seed file, and leaves existing records in place. It does not add synthetic interaction data.

The seed source is `src/main/resources/catalog/curated-resources.tsv`. Descriptions are generated as two-sentence summaries that identify the linked public source and topic tags; they are not copied from the linked pages. The seed contains 34 Technology, 35 Business & Finance, 35 Learning & Career, 35 Health & Fitness, and 36 Creative & Lifestyle entries, bringing the initial five categories to 40 each when the original 25 seed items are present. It includes eight supported types and all three named difficulty levels.

Run `npm run check:catalog-links` to request each unique URL from the packaged Flyway V3 starter seed and curated TSV. It tries HEAD first and falls back to GET, with bounded timeouts. To include the live public catalog (including admin-imported active items), set `CATALOG_API_URL=http://localhost:8080/api/items` before running it. The check requires outbound network access; network errors are reported as `ERROR`, not as evidence that a link is broken. The review inventory at [catalog-links-unverified.md](catalog-links-unverified.md) records links that could not be verified in an offline environment.

## Admin CSV import

The admin workspace accepts CSV with this exact header order:

```csv
category,title,description,type,url,source,difficulty,estimatedMinutes,tags
```

Use the downloadable template at `/catalog-import-template.csv`. Required columns must be present; descriptions need at least two sentences; URLs must be absolute HTTP(S); category names must match an active category; types and difficulty must be supported enum values; estimated time must be 1–1440 minutes; and `tags` must contain 3–6 active interest names separated by `|`. The service accepts up to 500 data rows and a maximum CSV body of 1 MB. CSV quoting supports commas, quotes, and line breaks in cells.

Preview and commit are admin-only:

- `POST /api/admin/items/import/preview` with `{"csv":"..."}` validates all rows and reports row-level errors without writing data.
- `POST /api/admin/items/import/commit` repeats all validation, then inserts all valid new rows and topic mappings in one transaction. Existing URLs are skipped and counted; other errors roll back the import.
- `PATCH /api/admin/items/active` with `{"itemIds":[1,2],"active":false}` changes up to 500 selected records. Every ID is checked before the update; activating an item also requires its category to be active.

The browser admin form previews rows before enabling the import button. Bulk changes require selecting item rows first. All data access uses parameterized Spring JDBC queries.

## Cold-start behavior

The existing recommendation service uses onboarding category/topic/goal signals, stored ratings and interactions, and evidence-gated popularity/collaboration signals. With sparse evidence, the feed marks cold-start picks as exploratory and tells the user that more feedback is needed. It does not claim a prediction from unavailable behavior. The starter catalog marks five resources in each niche as editor picks. This explicit admin-controlled signal adds five points to the ranking breakdown and gives cold-start users an explainable exploratory option; it is not presented as behavioral evidence. Admins can change the flag in the item form.
