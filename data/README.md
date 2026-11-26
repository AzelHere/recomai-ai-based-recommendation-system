# Reviewed educational data

The production resource catalog, external research and isolated software fixtures remain separate. No external learner is a Recom AI account. Processed metadata has separate data licenses: see licenses/attribution.md and source-registry.csv.

Reproduce from the repository root in PowerShell with Java 21:

```powershell
.\mvnw.cmd -f backend/pom.xml compile dependency:build-classpath '-Dmdep.outputFile=target/runtime-classpath.txt'
$catalogClasspath = 'backend/target/classes;' + (Get-Content backend/target/runtime-classpath.txt -Raw).Trim()
java -cp $catalogClasspath com.guvi.recommendationsystem.catalog.RealDataAcquisition data
node scripts/evaluate-imported-catalog.mjs
java -Xmx512m -cp $catalogClasspath com.guvi.recommendationsystem.evaluation.OuladEvaluation data/evaluation
java -cp $catalogClasspath com.guvi.recommendationsystem.catalog.ValidateSourceSample data
```

Acquisition reuses existing raw cache files. To intentionally refresh a source, move its individual cache files to a dated local archive first. The tool never deletes data or executes downloaded code. It uses reviewed fixed endpoints, sequential rate limiting, bounded retries/redirects/time/bytes and source-specific failure reporting. No network access occurs during ordinary imports or recommendations.

Admin → Import → Verified-source imports accepts processed/educational-resources.json, previews actual decisions and requires explicit import. The browser uses at most 250 records per transaction within the 500-record/3 MB server limit. Exact matches are preserved; uncertain editions go to review. Completed batches remain after a later failure, with safe retries. The JSON is the lossless import format; CSV formula-leading text is escaped for spreadsheet safety.

A local operator may alternatively invoke catalog.VerifiedCatalogImport from the packaged JAR using preview/apply, the reviewed JSON path and the exact previewed file SHA-256 for apply. It uses the same database service/transactions and never runs automatically at startup. Examples and actual execution evidence are in the ingestion report. Never run tests against valuable databases.

raw/ and evaluation/raw/ are local ignored caches and excluded from the ZIP. processed/ holds licensed metadata; rejected/ stores IDs and reasons; manifests/ holds checksums, acquisition times and separate live URL checks; evaluation/ holds declared judgments and aggregate results; licenses/ records restrictions and attribution. Source topics remain provider metadata; primary categories and controlled aliases are derived. Unknown difficulty is UNKNOWN in MySQL, unknown duration/price is null. No external ratings, enrollments, certificates or completion claims are invented.

The final catalog evaluation uses processed/imported-educational-resources.json (1,665 actually inserted provider records) and the public imported-resource-keys.json membership snapshot, excluding 105 held editions. The Node helper only stages these files and invokes the existing Java evaluator. Research scoring/metrics remain entirely Java. The original 1,770-record validated acquisition snapshot is retained for import preview and edition review.


## Verified article expansion

`processed/educational-articles.json` contains 500 genuine textual readings: 399 explicitly labeled OpenStax textbook sections and 101 individual Microsoft Learn/NLM explanatory articles. Distribution: Technology 150, Business & Finance 100, Learning & Career 100, Health & Fitness 100, Creative & Lifestyle 50. Registry, per-record attribution, acquisition HTTP receipts and checksums retain actual source rights and availability evidence. This is not a claim of 500 standalone journal/news articles.

Reproduce using the compiled classpath above:

```powershell
java '-Djava.net.preferIPv4Stack=true' -cp $catalogClasspath com.guvi.recommendationsystem.catalog.ArticleAcquisition data
java -cp $catalogClasspath com.guvi.recommendationsystem.catalog.VerifiedCatalogImport preview data/processed/educational-articles.json
# Review the preview; apply requires its exact file SHA256. Use the intended configured local database only.
java -cp $catalogClasspath com.guvi.recommendationsystem.catalog.VerifiedCatalogImport apply data/processed/educational-articles.json <reviewed-file-sha256>
java -cp $catalogClasspath com.guvi.recommendationsystem.catalog.ArticleAvailabilityImport data/processed/educational-articles.json data/manifests/article-acquisition.json
```

IPv4 selection avoids the host's NAT64 addresses, which the existing security guard rejects; public-address socket validation is unchanged. Raw caches and their receipt sidecars remain local and excluded from packages. A cache without a receipt gets a fresh bounded HEAD verification before availability is recorded. No ordinary startup acquires or imports this dataset. V18 registers reviewed sources and guards ten curated landing-page classification corrections. Fresh seed classifications match. Historical ranking metrics use the older 1,665-import subset, not this expansion.
