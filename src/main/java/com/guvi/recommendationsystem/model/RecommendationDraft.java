package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

public record RecommendationDraft(
        long itemId,
        String itemTitle,
        BigDecimal score,
        String reason) {
}
