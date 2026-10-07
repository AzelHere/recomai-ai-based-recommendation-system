package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;

import java.util.List;

public interface RecommendationDao {
    List<RecommendationCandidate> findCandidates(long userId);

    long createBatch(long userId, String algorithmVersion);

    PersonalizedRecommendation save(long userId, long batchId, RecommendationDraft recommendation);

    List<PersonalizedRecommendation> findLatestForUser(long userId, int limit);
}
