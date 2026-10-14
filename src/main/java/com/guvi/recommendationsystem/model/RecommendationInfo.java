package com.guvi.recommendationsystem.model;

import java.util.Map;

public record RecommendationInfo(Map<String, Double> feedMix, Map<String, Double> interactionWeights,
                                 String scoreMeaning, int popularityMinimumUsers,
                                 int collaborativeMinimumUsers) {
    public RecommendationInfo {
        feedMix = Map.copyOf(feedMix);
        interactionWeights = Map.copyOf(interactionWeights);
    }
}
