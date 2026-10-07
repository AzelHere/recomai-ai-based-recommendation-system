package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import com.guvi.recommendationsystem.recommendation.RecommendationEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecommendationService {
    private static final int MAX_RECOMMENDATIONS = 50;

    private final RecommendationDao recommendationDao;
    private final UserDao userDao;
    private final RecommendationEngine recommendationEngine;

    public RecommendationService(
            RecommendationDao recommendationDao,
            UserDao userDao,
            RecommendationEngine recommendationEngine) {
        this.recommendationDao = recommendationDao;
        this.userDao = userDao;
        this.recommendationEngine = recommendationEngine;
    }

    @Transactional
    public List<PersonalizedRecommendation> generateForUser(long userId, int topN) {
        validateUserId(userId);
        validateLimit(topN);
        ensureUserExists(userId);

        List<RecommendationDraft> drafts = recommendationEngine.recommend(
                recommendationDao.findCandidates(userId),
                topN);
        long batchId = recommendationDao.createBatch(userId, "content-score-v1");
        List<PersonalizedRecommendation> savedRecommendations = new ArrayList<>(drafts.size());
        for (RecommendationDraft draft : drafts) {
            savedRecommendations.add(recommendationDao.save(userId, batchId, draft));
        }
        return List.copyOf(savedRecommendations);
    }

    public List<PersonalizedRecommendation> getLatestForUser(long userId, int limit) {
        validateUserId(userId);
        validateLimit(limit);
        ensureUserExists(userId);
        return recommendationDao.findLatestForUser(userId, limit);
    }

    private void ensureUserExists(long userId) {
        if (!userDao.existsById(userId)) {
            throw new UserNotFoundException("User was not found.");
        }
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            throw new InvalidUserInputException("User ID must be positive.");
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_RECOMMENDATIONS) {
            throw new InvalidUserInputException("Recommendation count must be between 1 and 50.");
        }
    }
}
