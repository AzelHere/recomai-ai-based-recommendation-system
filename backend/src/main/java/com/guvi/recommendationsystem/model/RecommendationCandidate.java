package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

public record RecommendationCandidate(
        long itemId,
        String itemTitle,
        BigDecimal categoryPreferenceWeight,
        BigDecimal interestMatch,
        BigDecimal userAverageRating,
        BigDecimal itemAverageRating,
        BigDecimal positiveInteractionSignal,
        BigDecimal negativeInteractionSignal,
        BigDecimal qualityScore,
        String categoryName,
        String matchedTopics,
        BigDecimal relatedInterestMatch,
        BigDecimal purposeMatch,
        BigDecimal collaborativeMatch,
        int collaborativeEvidenceCount,
        BigDecimal popularityMatch,
        int popularityEvidenceCount,
        BigDecimal contentSimilarity,
        String contentMatchTerms,
        boolean editorPick) {
    public RecommendationCandidate(long itemId, String itemTitle, BigDecimal categoryPreferenceWeight,
                                   BigDecimal interestMatch, BigDecimal userAverageRating,
                                   BigDecimal itemAverageRating, BigDecimal positiveInteractionSignal,
                                   BigDecimal negativeInteractionSignal, BigDecimal qualityScore,
                                   String categoryName, String matchedTopics, BigDecimal relatedInterestMatch,
                                   BigDecimal purposeMatch, BigDecimal collaborativeMatch,
                                   int collaborativeEvidenceCount, BigDecimal popularityMatch,
                                   int popularityEvidenceCount, BigDecimal contentSimilarity, String contentMatchTerms) {
        this(itemId, itemTitle, categoryPreferenceWeight, interestMatch, userAverageRating, itemAverageRating,
                positiveInteractionSignal, negativeInteractionSignal, qualityScore, categoryName, matchedTopics,
                relatedInterestMatch, purposeMatch, collaborativeMatch, collaborativeEvidenceCount,
                popularityMatch, popularityEvidenceCount, contentSimilarity, contentMatchTerms, false);
    }

    public RecommendationCandidate(long itemId, String itemTitle, BigDecimal categoryPreferenceWeight,
                                   BigDecimal interestMatch, BigDecimal userAverageRating,
                                   BigDecimal itemAverageRating, BigDecimal positiveInteractionSignal,
                                   BigDecimal negativeInteractionSignal, BigDecimal qualityScore,
                                   String categoryName, String matchedTopics, BigDecimal relatedInterestMatch,
                                   BigDecimal purposeMatch) {
        this(itemId, itemTitle, categoryPreferenceWeight, interestMatch, userAverageRating, itemAverageRating,
                positiveInteractionSignal, negativeInteractionSignal, qualityScore, categoryName, matchedTopics,
                relatedInterestMatch, purposeMatch, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, "", false);
    }

    public RecommendationCandidate(long itemId, String itemTitle, BigDecimal categoryPreferenceWeight,
                                   BigDecimal interestMatch, BigDecimal userAverageRating,
                                   BigDecimal itemAverageRating, BigDecimal positiveInteractionSignal,
                                   BigDecimal negativeInteractionSignal, BigDecimal qualityScore,
                                   String categoryName, String matchedTopics, BigDecimal relatedInterestMatch,
                                   BigDecimal purposeMatch, BigDecimal collaborativeMatch,
                                   int collaborativeEvidenceCount, BigDecimal popularityMatch,
                                   int popularityEvidenceCount) {
        this(itemId, itemTitle, categoryPreferenceWeight, interestMatch, userAverageRating, itemAverageRating,
                positiveInteractionSignal, negativeInteractionSignal, qualityScore, categoryName, matchedTopics,
                relatedInterestMatch, purposeMatch, collaborativeMatch, collaborativeEvidenceCount,
                popularityMatch, popularityEvidenceCount, BigDecimal.ZERO, "", false);
    }

    public RecommendationCandidate(long itemId, String itemTitle, BigDecimal categoryPreferenceWeight,
                                   BigDecimal interestMatch, BigDecimal userAverageRating,
                                   BigDecimal itemAverageRating, BigDecimal positiveInteractionSignal,
                                   BigDecimal negativeInteractionSignal) {
        this(itemId, itemTitle, categoryPreferenceWeight, interestMatch, userAverageRating,
                itemAverageRating, positiveInteractionSignal, negativeInteractionSignal,
                BigDecimal.valueOf(0.5), "", "", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, "", false);
    }
}
