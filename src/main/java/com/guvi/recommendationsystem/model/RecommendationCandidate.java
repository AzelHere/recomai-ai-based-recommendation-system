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
        BigDecimal negativeInteractionSignal) {
}
