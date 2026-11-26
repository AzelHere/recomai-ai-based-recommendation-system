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
    public AdminRecommendationInsights {
        mostRecommendedItems = mostRecommendedItems == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(mostRecommendedItems));
        mostInteractedItems = mostInteractedItems == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(mostInteractedItems));
        mostSavedItems = mostSavedItems == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(mostSavedItems));
        mostDislikedItems = mostDislikedItems == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(mostDislikedItems));
        topRecommendedCategories = topRecommendedCategories == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(topRecommendedCategories));
    }

}
