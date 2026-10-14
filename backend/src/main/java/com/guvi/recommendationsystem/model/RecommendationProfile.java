package com.guvi.recommendationsystem.model;

import java.util.List;

public record RecommendationProfile(
        UserProfile profile,
        List<UserPreference> categoryPreferences,
        List<UserInterest> interests,
        long interactionCount,
        long feedbackCount) {
}
