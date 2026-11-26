package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

/** Observed feed views and outbound clicks; unavailable is distinct from a measured zero rate. */
public record RecommendationEngagementMetrics(
        long impressions,
        long clicks,
        BigDecimal clickThroughRatePercent,
        boolean available) {
}
