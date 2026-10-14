package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record PersonalizedRecommendation(
        long recommendationId,
        Long batchId,
        long itemId,
        String itemTitle,
        BigDecimal score,
        String reason,
        String reasonType,
        String bucketType,
        LocalDateTime generatedAt,
        Map<String, BigDecimal> scoreBreakdown) {
    public PersonalizedRecommendation(long recommendationId, Long batchId, long itemId, String itemTitle,
                                     BigDecimal score, String reason, String reasonType, String bucketType,
                                     LocalDateTime generatedAt) {
        this(recommendationId, batchId, itemId, itemTitle, score, reason, reasonType, bucketType,
                generatedAt, Map.of());
    }

    public PersonalizedRecommendation(long recommendationId, Long batchId, long itemId, String itemTitle,
                                      BigDecimal score, String reason, LocalDateTime generatedAt) {
        this(recommendationId, batchId, itemId, itemTitle, score, reason,
                "PROFILE_MATCH", "FAMILIAR", generatedAt, Map.of());
    }

    public PersonalizedRecommendation {
        scoreBreakdown = scoreBreakdown == null ? Map.of() : Map.copyOf(scoreBreakdown);
    }
}
