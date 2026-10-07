package com.guvi.recommendationsystem.recommendation;

import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class ScoringRecommendationEngine implements RecommendationEngine {
    private static final double CATEGORY_WEIGHT = 30.0;
    private static final double INTEREST_WEIGHT = 30.0;
    private static final double RATING_WEIGHT = 20.0;
    private static final double INTERACTION_WEIGHT = 20.0;

    @Override
    public List<RecommendationDraft> recommend(List<RecommendationCandidate> candidates, int topN) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be positive.");
        }

        List<ScoredCandidate> scoredCandidates = new ArrayList<>();
        for (RecommendationCandidate candidate : candidates) {
            if (candidate != null) {
                scoredCandidates.add(score(candidate));
            }
        }

        return scoredCandidates.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::rawScore).reversed()
                        .thenComparingLong(value -> value.candidate().itemId()))
                .limit(topN)
                .map(ScoredCandidate::draft)
                .toList();
    }

    private ScoredCandidate score(RecommendationCandidate candidate) {
        double categoryMatch = unitValue(candidate.categoryPreferenceWeight());
        double interestMatch = unitValue(candidate.interestMatch());
        RatingMatch ratingMatch = ratingMatch(candidate);
        double positiveSignal = nonNegative(candidate.positiveInteractionSignal());
        double negativeSignal = nonNegative(candidate.negativeInteractionSignal());

        // The tanh transform keeps interaction impact bounded while preserving a negative penalty.
        double interactionEffect = Math.tanh((positiveSignal - negativeSignal) / 2.0);
        double rawScore = CATEGORY_WEIGHT * categoryMatch
                + INTEREST_WEIGHT * interestMatch
                + RATING_WEIGHT * ratingMatch.value()
                + INTERACTION_WEIGHT * interactionEffect;
        double storedScore = Math.max(0.0, rawScore);

        return new ScoredCandidate(
                candidate,
                rawScore,
                BigDecimal.valueOf(storedScore).setScale(4, RoundingMode.HALF_UP),
                explain(categoryMatch, interestMatch, ratingMatch, positiveSignal, negativeSignal));
    }

    private RatingMatch ratingMatch(RecommendationCandidate candidate) {
        BigDecimal personalAverage = candidate.userAverageRating();
        BigDecimal itemAverage = candidate.itemAverageRating();
        if (itemAverage == null) {
            return new RatingMatch(0.0, "no rating signal");
        }
        if (personalAverage == null) {
            return new RatingMatch(unitValue(itemAverage.divide(BigDecimal.valueOf(5), 4, RoundingMode.HALF_UP)),
                    "has community ratings");
        }
        double difference = Math.abs(personalAverage.doubleValue() - itemAverage.doubleValue());
        double similarity = Math.max(0.0, 1.0 - difference / 4.0);
        return new RatingMatch(similarity, "has ratings similar to yours");
    }

    private String explain(
            double categoryMatch,
            double interestMatch,
            RatingMatch ratingMatch,
            double positiveSignal,
            double negativeSignal) {
        List<ReasonContribution> contributions = new ArrayList<>();
        if (categoryMatch > 0.0) {
            contributions.add(new ReasonContribution(CATEGORY_WEIGHT * categoryMatch,
                    "matches a category you prefer"));
        }
        if (interestMatch > 0.0) {
            contributions.add(new ReasonContribution(INTEREST_WEIGHT * interestMatch,
                    "matches your interests"));
        }
        if (ratingMatch.value() > 0.0) {
            contributions.add(new ReasonContribution(RATING_WEIGHT * ratingMatch.value(), ratingMatch.description()));
        }
        if (Math.tanh((positiveSignal - negativeSignal) / 2.0) > 0.0) {
            contributions.add(new ReasonContribution(
                    INTERACTION_WEIGHT * Math.max(0.0, Math.tanh((positiveSignal - negativeSignal) / 2.0)),
                    "matches your positive activity"));
        }
        contributions.sort(Comparator.comparingDouble(ReasonContribution::value).reversed());

        String reason;
        if (contributions.isEmpty()) {
            reason = "No strong preference or rating signal is available yet.";
        } else {
            reason = "Recommended because it " + contributions.getFirst().description() + ".";
        }
        if (negativeSignal > 0.0) {
            reason += " Your previous dislike or hide activity reduced its score.";
        }
        return reason;
    }

    private double unitValue(BigDecimal value) {
        if (value == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value.doubleValue()));
    }

    private double nonNegative(BigDecimal value) {
        return value == null ? 0.0 : Math.max(0.0, value.doubleValue());
    }

    private record RatingMatch(double value, String description) {
    }

    private record ReasonContribution(double value, String description) {
    }

    private record ScoredCandidate(
            RecommendationCandidate candidate,
            double rawScore,
            BigDecimal score,
            String reason) {
        private RecommendationDraft draft() {
            return new RecommendationDraft(candidate.itemId(), candidate.itemTitle(), score, reason);
        }
    }
}
