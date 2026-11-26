# Recom AI recommendation algorithms

[Exact coefficients, TF-IDF equations, numerical examples, decay and limitations](../technical/Recommendation-Engine.md).

[ContentFeatureScorer](../../backend/src/main/java/com/guvi/recommendationsystem/recommendation/ContentFeatureScorer.java) computes logarithmic TF, smoothed IDF, L2 normalization and cosine similarity. Empty vectors stay zero. Immutable corpus snapshots are rebuilt as a unit; user profiles are request-specific.

[ScoringRecommendationEngine](../../backend/src/main/java/com/guvi/recommendationsystem/recommendation/ScoringRecommendationEngine.java) combines category, topic, related-topic, content, rating, interaction, popularity, evidence-gated collaborative, purpose and editor signals. Points are not probabilities. Negative feedback excludes or penalizes candidates. Default bucket shares are 70/20/10; deterministic ties use item IDs. Category diversity only substitutes near ties.

[LearningJourneyService](../../backend/src/main/java/com/guvi/recommendationsystem/service/LearningJourneyService.java) persists bounded, owner-scoped suggested sequences using existing resources and explicit progress. It does not infer completion from a click or certify prerequisites.

[Workflow diagrams](../architecture/workflows.md). Historical catalog evaluation used 1,665 imports and seven assistant-judged queries; it was not evaluated on the 2,365-resource expansion or validated by independent human raters. OULAD item-cosine is a separate offline research experiment, not a claim of validated production collaborative learning or learning gain.
