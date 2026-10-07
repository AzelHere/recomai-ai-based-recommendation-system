package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.util.List;

public record AdminRecommendationInsights(
        List<ItemInsight> mostRecommendedItems,
        List<ItemInsight> mostInteractedItems,
        List<ItemInsight> mostSavedItems,
        List<ItemInsight> mostDislikedItems,
        List<CategoryInsight> topRecommendedCategories,
        BigDecimal averageRecommendationScore) {
}
