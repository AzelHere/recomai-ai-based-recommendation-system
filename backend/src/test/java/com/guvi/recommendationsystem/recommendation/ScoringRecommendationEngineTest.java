package com.guvi.recommendationsystem.recommendation;

import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ScoringRecommendationEngineTest {
    private final ScoringRecommendationEngine engine = new ScoringRecommendationEngine();

    @Test
    void ranksItemsFromCategoryInterestRatingAndPositiveActivitySignals() {
        RecommendationCandidate strongMatch = candidate(
                8, "Strong match", "0.8000", "0.9000", "4.00", "4.50", "2.00", "0.00");
        RecommendationCandidate weakMatch = candidate(
                3, "Weak match", "0.1000", "0.1000", "4.00", "2.00", "0.00", "0.00");

        List<RecommendationDraft> results = engine.recommend(List.of(weakMatch, strongMatch), 10);

        assertThat(results).extracting(RecommendationDraft::itemTitle)
                .containsExactly("Strong match", "Weak match");
        assertThat(results.getFirst().score()).isGreaterThan(results.getLast().score());
        assertThat(results.getFirst().reason()).contains("recent activity suggests interest");
    }

    @Test
    void negativeInteractionReducesScoreAndIsMentionedInReason() {
        RecommendationCandidate neutral = candidate(
                1, "Item", "0.50", "0.50", "4.00", "4.00", "0.00", "0.00");
        RecommendationCandidate skipped = candidate(
                2, "Item", "0.50", "0.50", "4.00", "4.00", "0.00", "0.50");

        RecommendationDraft baseline = engine.recommend(List.of(neutral), 1).getFirst();
        RecommendationDraft penalized = engine.recommend(List.of(skipped), 1).getFirst();

        assertThat(penalized.score()).isLessThan(baseline.score());
        assertThat(penalized.reason()).contains("reduced its score");
    }

    @Test
    void stronglyRejectedItemsAreSuppressedFromTheFeed() {
        RecommendationCandidate hidden = candidate(
                2, "Hidden", "0.90", "1.00", null, null, "0.00", "1.50");
        assertThat(engine.recommend(List.of(hidden), 1)).isEmpty();
    }

    @Test
    void feedUsesFamiliarRelatedAndDiscoveryBucketsWhenAvailable() {
        RecommendationCandidate familiar = candidate(1, "Familiar", "0.8", "0.9", null, null, "1.0", "0");
        RecommendationCandidate related = new RecommendationCandidate(2, "Related", decimal("0"), decimal("0"),
                null, null, decimal("0"), decimal("0"), decimal("0.5"), "Technology", "", decimal("0.8"), decimal("0"));
        RecommendationCandidate discovery = candidate(3, "Discovery", "0", "0", null, null, "0", "0");

        List<RecommendationDraft> results = engine.recommend(
                List.of(discovery, related, familiar), 10);

        assertThat(results).extracting(RecommendationDraft::bucketType)
                .contains("FAMILIAR", "RELATED", "DISCOVERY");
        assertThat(results).extracting(RecommendationDraft::reasonType)
                .contains("RECENT_ACTIVITY", "RELATED_INTEREST", "COLD_START");
    }

    @Test
    void sortsTiesByItemIdAndHonorsTopN() {
        RecommendationCandidate second = candidate(
                2, "Second", "0.50", "0.50", null, null, "0.00", "0.00");
        RecommendationCandidate first = candidate(
                1, "First", "0.50", "0.50", null, null, "0.00", "0.00");

        List<RecommendationDraft> results = engine.recommend(List.of(second, first), 1);

        assertThat(results).extracting(RecommendationDraft::itemTitle).containsExactly("First");
    }

    @Test
    void coldStartStillReturnsExplainableDiscoveryCandidates() {
        RecommendationCandidate newTopic = candidate(
                14, "Starter resource", "0", "0", null, "4.50", "0", "0");

        RecommendationDraft result = engine.recommend(List.of(newTopic), 5).getFirst();

        assertThat(result.bucketType()).isEqualTo("DISCOVERY");
        assertThat(result.reasonType()).isEqualTo("COLD_START");
        assertThat(result.reason()).contains("not enough preference or activity evidence");
    }

    @Test
    void categoryPreferenceAddsTwelveRankingPointsAndRanksOtherwiseEqualItemsHigher() {
        RecommendationCandidate preferred = candidate(
                1, "Preferred category", "1.00", "0.00", null, null, "0.00", "0.00");
        RecommendationCandidate unpreferred = candidate(
                2, "Unpreferred category", "0.00", "0.00", null, null, "0.00", "0.00");

        List<RecommendationDraft> results = engine.recommend(List.of(unpreferred, preferred), 2);

        assertThat(results).extracting(RecommendationDraft::itemTitle)
                .containsExactly("Preferred category", "Unpreferred category");
        assertThat(results.getFirst().scoreBreakdown())
                .containsEntry("category_preference", decimal("12.0000"));
        assertThat(results.getFirst().score()).isGreaterThan(results.getLast().score());
    }

    @Test
    void editorialSelectionCanSeedColdStartAndIsVisibleInTheBreakdown() {
        RecommendationCandidate pick = new RecommendationCandidate(15, "Curated start",
                decimal("0"), decimal("0"), null, null, decimal("0"), decimal("0"), decimal("0.5"),
                "Technology", "", decimal("0"), decimal("0"), decimal("0"), 0,
                decimal("0"), 0, decimal("0"), "", true);

        RecommendationDraft result = engine.recommend(List.of(pick), 5).getFirst();

        assertThat(result.bucketType()).isEqualTo("DISCOVERY");
        assertThat(result.reasonType()).isEqualTo("EDITOR_PICK");
        assertThat(result.reason()).contains("Editor's pick");
        assertThat(result.scoreBreakdown()).containsEntry("editor_pick", decimal("5.0000"));
    }

    @Test
    void topFiftyDoesNotPromiseAnArbitraryLowRankedCatalogItem() {
        List<RecommendationCandidate> catalog = new java.util.ArrayList<>();
        for (long itemId = 1; itemId <= 200; itemId++) {
            boolean editorPick = itemId <= 25;
            catalog.add(new RecommendationCandidate(itemId, "Item " + itemId, decimal("0"), decimal("0"),
                    null, null, decimal("0"), decimal("0"), decimal("0.5"), "Creative", "",
                    decimal("0"), decimal("0"), decimal("0"), 0, decimal("0"), 0,
                    decimal("0"), "", editorPick));
        }

        List<RecommendationDraft> results = engine.recommend(catalog, 50);

        assertThat(results).hasSize(50);
        assertThat(results).extracting(RecommendationDraft::itemId).contains(25L).doesNotContain(200L);
    }

    @Test
    void scoreBreakdownReconcilesToRankingScoreAndDoesNotClaimProbability() {
        RecommendationDraft result = engine.recommend(List.of(candidate(
                4, "Transparent", "0.80", "0.70", "4.0", "4.5", "1.2", "0.1")), 1).getFirst();

        BigDecimal components = result.scoreBreakdown().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(components).isEqualByComparingTo(result.score());
        assertThat(result.score()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(result.scoreBreakdown()).containsKeys("category_preference", "selected_topic_match",
                "negative_feedback_penalty", "collaborative_evidence");
    }

    @Test
    void sparseCollaborativeSignalsAreNotUsedOrDescribedAsEvidence() {
        RecommendationCandidate candidate = new RecommendationCandidate(7, "Sparse", decimal("0"), decimal("0"),
                null, null, decimal("0"), decimal("0"), decimal("0.5"), "Learning", "",
                decimal("0"), decimal("0"), decimal("1"), 2, decimal("0"), 0);

        RecommendationDraft result = engine.recommend(List.of(candidate), 1).getFirst();

        assertThat(result.scoreBreakdown().get("collaborative_evidence")).isEqualByComparingTo("0.0000");
        assertThat(result.reason()).contains("too sparse").doesNotContain("People with overlapping");
    }

    @Test
    void collaborationContributesOnlyWithAtLeastThreeDistinctUsers() {
        RecommendationCandidate candidate = new RecommendationCandidate(7, "Neighbor pick", decimal("0"), decimal("0"),
                null, null, decimal("0"), decimal("0"), decimal("0.5"), "Learning", "",
                decimal("0"), decimal("0"), decimal("0.8"), 3, decimal("0"), 0);

        RecommendationDraft result = engine.recommend(List.of(candidate), 1).getFirst();

        assertThat(result.reasonType()).isEqualTo("COLLABORATIVE");
        assertThat(result.scoreBreakdown().get("collaborative_evidence")).isEqualByComparingTo("8.0000");
        assertThat(result.reason()).contains("overlapping positive item activity");
    }

    @Test
    void metadataSimilarityExplanationUsesActualOverlappingTerms() {
        RecommendationCandidate candidate = new RecommendationCandidate(9, "Related resource", decimal("0"),
                decimal("0"), null, null, decimal("0"), decimal("0"), decimal("0.5"), "Technology", "",
                decimal("0"), decimal("0"), decimal("0"), 0, decimal("0"), 0,
                decimal("0.75"), "java, backend");

        RecommendationDraft result = engine.recommend(List.of(candidate), 1).getFirst();

        assertThat(result.reasonType()).isEqualTo("CONTENT_SIMILARITY");
        assertThat(result.reason()).contains("java, backend");
        assertThat(result.scoreBreakdown().get("metadata_similarity")).isEqualByComparingTo("6.0000");
    }

    @Test
    void repeatedEventsFromOneOtherUserDoNotQualifyAsCatalogPopularity() {
        // The DAO reports distinct users as evidence, even if one user generated many events.
        RecommendationCandidate sparse = new RecommendationCandidate(10, "Sparse trend", decimal("0"), decimal("0"),
                null, null, decimal("0"), decimal("0"), decimal("0.5"), "Technology", "",
                decimal("0"), decimal("0"), decimal("0"), 0, decimal("1"), 1);
        RecommendationCandidate supported = new RecommendationCandidate(11, "Supported trend", decimal("0"),
                decimal("0"), null, null, decimal("0"), decimal("0"), decimal("0.5"), "Technology", "",
                decimal("0"), decimal("0"), decimal("0"), 0, decimal("1"), 3);

        RecommendationDraft unsupportedResult = engine.recommend(List.of(sparse), 1).getFirst();
        RecommendationDraft supportedResult = engine.recommend(List.of(supported), 1).getFirst();

        assertThat(unsupportedResult.reasonType()).isEqualTo("COLD_START");
        assertThat(unsupportedResult.scoreBreakdown().get("catalog_popularity")).isEqualByComparingTo("0.0000");
        assertThat(supportedResult.reasonType()).isEqualTo("POPULARITY");
        assertThat(supportedResult.scoreBreakdown().get("catalog_popularity")).isEqualByComparingTo("10.0000");
    }

    @Test
    void categorySpecificRatingAffinityRanksItemsCloserToTheUsersRatingHigher() {
        RecommendationCandidate close = candidate(
                1, "Close rating", "0", "0", "5.00", "4.50", "0", "0");
        RecommendationCandidate distant = candidate(
                2, "Distant rating", "0", "0", "5.00", "1.00", "0", "0");

        List<RecommendationDraft> results = engine.recommend(List.of(distant, close), 2);

        assertThat(results).extracting(RecommendationDraft::itemTitle)
                .containsExactly("Close rating", "Distant rating");
    }

    @Test
    void explicitRatingAffinityAndInteractionEvidenceAreSeparateScoreComponents() {
        RecommendationCandidate ratingOnly = candidate(
                1, "Rating match", "0", "0", "5.00", "4.50", "0", "0");
        RecommendationCandidate interactionOnly = candidate(
                2, "Interaction match", "0", "0", null, null, "2.00", "0");

        List<RecommendationDraft> ranked = engine.recommend(List.of(ratingOnly, interactionOnly), 2);

        RecommendationDraft rate = ranked.stream().filter(item -> item.itemId() == 1).findFirst().orElseThrow();
        RecommendationDraft action = ranked.stream().filter(item -> item.itemId() == 2).findFirst().orElseThrow();
        assertThat(rate.scoreBreakdown().get("rating_affinity")).isGreaterThan(BigDecimal.ZERO);
        assertThat(rate.scoreBreakdown().get("positive_interaction")).isEqualByComparingTo("0.0000");
        assertThat(action.scoreBreakdown().get("positive_interaction")).isGreaterThan(BigDecimal.ZERO);
        assertThat(action.scoreBreakdown().get("rating_affinity")).isEqualByComparingTo("0.0000");
        assertThat(ranked).allSatisfy(entry -> assertThat(entry.scoreBreakdown().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(entry.score()));
    }

    @Test
    void rejectsNonPositiveTopN() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> engine.recommend(List.of(), 0));
    }

    private RecommendationCandidate candidate(
            long id,
            String title,
            String category,
            String interests,
            String personalRating,
            String itemRating,
            String positive,
            String negative) {
        return new RecommendationCandidate(
                id,
                title,
                decimal(category),
                decimal(interests),
                decimal(personalRating),
                decimal(itemRating),
                decimal(positive),
                decimal(negative));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
