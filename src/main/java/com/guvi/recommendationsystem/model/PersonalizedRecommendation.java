package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PersonalizedRecommendation(
        long recommendationId,
        Long batchId,
        long itemId,
        String itemTitle,
        BigDecimal score,
        String reason,
        LocalDateTime generatedAt) {
}
