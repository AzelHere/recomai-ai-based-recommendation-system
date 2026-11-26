# Recom AI current-catalog ranking evaluation

This is a deterministic editorial metadata-proxy experiment, not a human study or a measurement of learning outcomes. Twenty assistant-authored profiles span all five categories, with four topic/goal contexts per category. The frozen set contains 465 graded profile/resource judgments over 281 distinct genuine resources from the 2,365-resource catalog. No real user records or invented interaction histories are used: the isolated evaluation database has zero interaction rows.

The [profiles](../../data/evaluation/current-profiles.json), [judgments](../../data/evaluation/current-judgments.csv), [per-profile results](recommendation-results.csv), [rankings](recommendation-rankings.tsv), [manifest](recommendation-manifest.json) and [before/after means](recommendation-means.csv) are published for inspection. The rubric is authored before model execution: grade 3 for topic intent in the title, 2 for controlled-topic evidence, 1 for description evidence, otherwise 0; a known advanced resource is penalized for a beginner context. Unknown levels remain unknown. This lexical rubric can favor keyword/content methods. Profile level is evaluation context, not a claim that the production engine learns skill level.

Each profile receives a purposive, type-stratified judged candidate pool with off-category controls. All four methods rank the same pool; the actual DAO supplies corpus TF-IDF/features and the actual production engine supplies hybrid scoring. This evaluates ranking conditional on the judged pool, not exhaustive catalog retrieval. Recall is **judged-pool recall**, not full-catalog recall. Pool selection can bias all reported metrics. Independent blinded human judgments and temporal real-interaction validation remain future work.

| Method, K=10 | Precision | Judged recall | Graded NDCG | MRR | Category pair diversity |
|---|---:|---:|---:|---:|---:|
| Popularity | .715 | .472 | .562 | .921 | .404 |
| Keyword | .930 | .613 | .762 | 1.000 | .109 |
| Content cosine | .950 | .625 | .863 | 1.000 | .068 |
| Production hybrid | .840 | .565 | .777 | 1.000 | .249 |

The hybrid does not beat content cosine on this relevance proxy. Its greater category diversity reflects the familiar/related/discovery allocation; no coefficient tuning was performed to win this test. Popularity is especially weak evidence here because all synthetic profiles have zero interaction history and popularity ties resolve by item ID. All before/after mean metrics are identical in the CSV; this phase does not claim a ranking-quality gain from resource cleanup or UI repairs.

Hybrid top-ten exposure across twenty profiles covers 156 distinct resources (55.52% of the judged union; 6.60% of the full catalog). Its 200 recommendation slots contain 18 articles, 59 courses, 8 documentation resources, 49 guides, 17 projects, 30 general resources, 13 tools and 6 videos. Provider distributions are in the manifest; exposure variety does not imply fairness or educational quality.

Hand-calculated tests check log-TF and smoothed IDF, repeated terms, cosine normalization, zero vectors, absent profiles, stable explanations and graded NDCG/precision/recall/MRR boundaries. Existing tests cover decay, feedback, allocation and deterministic ranking. The normal integration tests exercise actual article filters, owner libraries and persisted journeys.

Reproduce with the compiled test classes and `QUALITY_IMAGE=<reviewed-image> node scripts/technical-measure.mjs evaluation`. The runner creates a unique loopback-only database, imports attributed metadata, refuses an unexpected database identity, executes the test-only `CurrentCatalogEvaluation` entry point, exports public results and stops only its own containers. It retains its volume. Run `evaluation-before` with the baseline image for comparison. The helper requires test classes compiled against compatible production APIs.
