package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

public record AdminDashboardSummary(
        long totalUsers,
        long activeUsersLast30Days,
        long totalItems,
        long totalCategories,
        long totalInteractions,
        long recommendationsGenerated,
        BigDecimal averageRating) {
}
