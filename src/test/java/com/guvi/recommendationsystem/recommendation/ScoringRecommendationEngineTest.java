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
        assertThat(results.getFirst().reason()).contains("matches your interests");
    }

    @Test
    void negativeInteractionReducesScoreAndIsMentionedInReason() {
        RecommendationCandidate neutral = candidate(
                1, "Item", "0.50", "0.50", "4.00", "4.00", "0.00", "0.00");
        RecommendationCandidate hidden = candidate(
                2, "Item", "0.50", "0.50", "4.00", "4.00", "0.00", "2.00");

        RecommendationDraft baseline = engine.recommend(List.of(neutral), 1).getFirst();
        RecommendationDraft penalized = engine.recommend(List.of(hidden), 1).getFirst();

        assertThat(penalized.score()).isLessThan(baseline.score());
        assertThat(penalized.reason()).contains("reduced its score");
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
