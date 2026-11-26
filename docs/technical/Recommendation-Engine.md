# Recommendation engine

## Beginner explanation
A learner chooses a purpose and topics, such as learning Java. Each resource has a title, description and topic metadata. The system compares these words with the selected topics and positively engaged resources, then combines that similarity with explicit category/topic preferences and bounded behavioral evidence. The learner receives explanations identifying those signals. Saving indicates interest; clicking does not mean completion. Journeys persist a recommended order and track explicit step status. They are suggestions, not verified university curricula.

## Implemented text representation
`ContentFeatureScorer` lowercases with Locale.ROOT, extracts Unicode letter/number tokens of length at least two and removes a fixed 19-token stop list. It does not stem, embed language with a transformer or call an LLM. For frequency f > 0, tf = 1 + ln(f). For corpus size N and document frequency df, idf = 1 + ln((N + 1)/(df + 1)). Raw weight is tf × idf. Each resource vector is L2 normalized. Positive-history vectors are averaged and normalized, then a separately normalized selected-topic vector is added and the combined profile is normalized again. A token outside the corpus has df=0; unseen terms can reduce similarity after normalization.

Cosine is the dot product of normalized vectors, bounded at one for output. Positively engaged seed items are excluded from the content-match map, while the full hybrid pipeline can use separate activity signals. The top three overlapping terms, ranked by component product and then lexical order, support the content explanation. An immutable corpus snapshot prevents partial rebuild visibility; catalog changes invalidate the corpus after successful commit. User profiles are recomputed rather than shared as corpus cache entries.

## Exact hybrid score
Let c, i, r, x, a, p, k and g denote clamped category preference, selected-topic match, related-topic match, content cosine, rating affinity, popularity, collaborative match and onboarding purpose. Let u and v be nonnegative positive/negative interaction aggregates and e be 1 for an editor pick, otherwise 0.

P = 12c + 24i + 10r + 8x + 7a + 16 tanh(u/2) + 10p + 10k + 3g + 5e.

S = P - min(P, 20 tanh(v/2)).

Components are stored as ranking points rounded to four decimal places; S is not a probability or confidence. Collaborative and popularity contributions are zero unless their respective evidence count is at least three. Candidates with negativeInteractionSignal >= 1.25 are excluded before scoring. Ties use item ID. Rating affinity is defined in the ranker's ratingMatch helper, reproduced in Source-Excerpts.md. Data access computes the candidate signals; do not treat all raw event values as unit scores.

Default familiar/related/discovery shares are 0.70/0.20/0.10. For exploration E, shift=(E-50)/100, retained=1-|shift|, adjusted familiar=.70×retained+max(0,-shift), related=.20×retained, discovery=.10×retained+max(0,shift). Bucket selection rounds familiar and related counts, assigns the remainder to discovery, and fills shortages. Category variety may substitute only within max(2 points, 10% of the best score). It is bounded near-tie diversification, not unrestricted randomization.

## Behavioral time decay
DAO queries apply 0.5^(ageHours/(24×45)) to relevant behavioral topic/item signals, and 60-day half-life to onboarding-derived category preferences. Explicit edits retain their source behavior. Onboarding purpose weakens with behavior maturity; recent exposure suppresses repetition. Separate collaborative/popularity queries use bounded 180-day evidence windows. A window is not the same as exponential decay. Source excerpts preserve the exact SQL and weight use.

## Worked TF-IDF example
Illustrative corpus: D1="java java basics", D2="java sql", D3="sql basics". N=3 and df(java)=df(sql)=df(basics)=2. Thus idf=1+ln(4/3)=1.287682. In D1, tf(java)=1+ln(2)=1.693147; raw java=2.180235 and raw basics=1.287682. Its norm is approximately 2.532105, so normalized D1=(java .861037, sql 0, basics .508542). D2=(.707107,.707107,0); D3=(0,.707107,.707107). A normalized Java-only query has cosine .861037 with D1, .707107 with D2, and zero with D3. Values are illustrative calculations, not measured catalog outcomes.

## Worked cosine and hybrid examples
For vectors (1,2) and (2,1), dot product=4, both magnitudes=sqrt(5), cosine=4/5=.8. With c=.5, i=1, r=.2, x=.8, a=.4, u=1, v=.2, p=k=0 (insufficient evidence), g=1 and e=0, P=6+24+2+6.4+2.8+7.393875+0+0+3=51.593875. The negative deduction is 20×tanh(.1)=1.993360, so S≈49.600515 before component rounding. Bucket logic then affects composition and diversity. A higher point score does not establish better learning outcomes.

## Complexity and limits
Index construction is linear in token occurrences plus normalization over distinct nonzero terms. One content request traverses the corpus vectors; candidate sorting is O(M log M), where M is candidate count. Diversity/bucket scans can be quadratic in the bounded selection process; no sublinear vector-search claim is made. Small-catalog SQL still scans some substring/sort expressions. Session, cache and rate state are process-local. Collaboration exists as gated sparse evidence, not a validated dense production learner model.

## Evaluation
Synthetic scenarios test deterministic rank behavior. The genuine 1,665-resource evaluation uses seven queries and 210 assistant-assigned judgments: mean NDCG@5 .678866 for the frozen baseline and .885226 with selected-topic TF-IDF. This is not independent human labeling. The separate OULAD CCC/2014B benchmark scans real interaction logs, selects 1,000 anonymous learners, trains on days 0–40, tests on 41–80 and retains 595 eligible histories and 94 training-known candidate items. Popularity NDCG@10=.1774672; item-cosine=.1889808. Future VLE access is the target, not satisfaction or learning gain. Raw research records are excluded from this submission.


## Current article integration update

The current inspected catalog has 2,365 resources: 200 preserved curated entries and 2,165 accepted imports. The expansion adds 500 verified textual resources classified ARTICLE, including 399 explicitly labeled OpenStax textbook reading sections and 101 individual explanatory articles. The ARTICLE filter now returns 505 resources. Original 1,465 MIT OCW imports, 200 MedlinePlus guides and 105 held edition reviews are retained.

The Technology category previously contained zero ARTICLE rows, while the global Articles API already returned 15. Verified ingestion excluded ARTICLE. Explore also advertised unsupported BOOK, and Topic was folded into OR-based search instead of independently intersecting filters. The correction accepts reviewed ARTICLE imports, aligns actual enum options, adds a bounded topic predicate and provider input, supplies an accurate empty state and adds administrator type filtering. Read Article actions use the existing cards. No article-specific ranking boost or completion-on-click was added. Flyway V18 adds approved source registrations and guards ten existing landing-page type corrections; V1 through V17 are unchanged.

Historical evaluation scope: the reported catalog NDCG@5 values .678866 and .885226 were measured on the earlier 1,665 accepted imported resources within the 1,865-resource catalog, using seven queries and 210 assistant-assigned judgments. They were NOT measured on the expanded article catalog. The independent OULAD task and its cutoff/eligibility limits remain unchanged. Current regression checks verify ranking behavior and article inclusion; no expanded-catalog NDCG or learning-outcome improvement is claimed.

New metadata sources: Microsoft Learn Java fundamentals and SQL overview (CC BY 4.0), OpenStax book-specific CC BY 4.0 or CC BY-NC-SA 4.0, MedlinePlus Genetics (government text metadata). Short publisher excerpts and original links are retained with attribution; full chapters, images and third-party material are excluded. OpenStax reading sections are not standalone journal/news articles. Noncommercial/share-alike restrictions remain applicable. The application uses TF-IDF and deterministic ranking, not an LLM content ingestion or training service.
