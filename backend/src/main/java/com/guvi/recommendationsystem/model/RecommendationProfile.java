package com.guvi.recommendationsystem.model;

import java.util.List;

public record RecommendationProfile(
        UserProfile profile,
        List<UserPreference> categoryPreferences,
        List<UserInterest> interests,
        long interactionCount,
        long feedbackCount) {
    public RecommendationProfile {
        categoryPreferences = categoryPreferences == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(categoryPreferences));
        interests = interests == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(interests));
    }

}
