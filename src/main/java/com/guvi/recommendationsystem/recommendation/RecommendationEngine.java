package com.guvi.recommendationsystem.recommendation;

import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;

import java.util.List;

public interface RecommendationEngine {
    List<RecommendationDraft> recommend(List<RecommendationCandidate> candidates, int topN);
}
