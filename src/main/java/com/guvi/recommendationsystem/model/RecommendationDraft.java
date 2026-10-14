package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.util.Map;

public record RecommendationDraft(
        long itemId,
        String itemTitle,
        BigDecimal score,
        String reason,
        String reasonType,
        String bucketType,
        Map<String, BigDecimal> scoreBreakdown) {
    public RecommendationDraft(long itemId, String itemTitle, BigDecimal score, String reason,
                               String reasonType, String bucketType) {
        this(itemId, itemTitle, score, reason, reasonType, bucketType, Map.of());
    }

    public RecommendationDraft(long itemId, String itemTitle, BigDecimal score, String reason) {
        this(itemId, itemTitle, score, reason, "PROFILE_MATCH", "FAMILIAR", Map.of());
    }

    public RecommendationDraft {
        scoreBreakdown = scoreBreakdown == null ? Map.of() : Map.copyOf(scoreBreakdown);
    }
}
