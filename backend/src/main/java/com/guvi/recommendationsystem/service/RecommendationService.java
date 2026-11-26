package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.UserPreferenceDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import com.guvi.recommendationsystem.model.CatalogItem;
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
    private final UserPreferenceDao preferences;
    private final ItemDao itemDao;

    public RecommendationService(
            RecommendationDao recommendationDao,
            UserDao userDao,
            RecommendationEngine recommendationEngine,
            ItemDao itemDao, UserPreferenceDao preferences) {
        this.preferences = preferences;
        this.recommendationDao = recommendationDao;
        this.userDao = userDao;
        this.recommendationEngine = recommendationEngine;
        this.itemDao = itemDao;
    }

    /** Locks the user's exploration preference and persists one complete ranking batch atomically.
     * A failed draft insert rolls back the batch, so readers never observe partial recommendations.
     * @param userId existing authenticated owner
     * @param topN requested count, from one through fifty
     * @return persisted recommendations in the selected feed order
     */
    @Transactional
    public List<PersonalizedRecommendation> generateForUser(long userId, int topN) {
        validateUserId(userId);
        validateLimit(topN);
        ensureUserExists(userId);

        return generateBatch(userId, topN, preferences.lockExploration(userId));
    }

    public int exploration(long userId) {
        return preferences.exploration(userId);
    }

    @Transactional
    public void updateExploration(long userId, java.math.BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(java.math.BigDecimal.valueOf(100)) > 0
                || value.stripTrailingZeros().scale() > 0) {
            throw new InvalidUserInputException("Exploration must be a whole number between 0 and 100.");
        }
        int exploration = value.intValueExact();
        if (preferences.lockExploration(userId) == exploration) return;
        preferences.saveExploration(userId, exploration);
        generateBatch(userId, 10, exploration);
    }

    private List<PersonalizedRecommendation> generateBatch(long userId, int topN, int exploration) {
        List<RecommendationDraft> drafts = recommendationEngine.recommend(
                recommendationDao.findCandidates(userId),
                topN, exploration);
        long batchId = recommendationDao.createBatch(userId, "hybrid-metadata-v4");
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

    public void recordImpression(long userId, long recommendationId) {
        validateRecommendationId(recommendationId);
        ensureUserExists(userId);
        recommendationDao.recordImpression(userId, recommendationId);
    }

    public void recordClick(long userId, long recommendationId) {
        validateRecommendationId(recommendationId);
        ensureUserExists(userId);
        recommendationDao.recordClick(userId, recommendationId);
    }

    /** A distinctly non-personalized fallback; it uses catalog curation/quality, never user scoring. */
    public List<CatalogItem> getCatalogDiscoveryForUser(long userId, int limit) {
        validateUserId(userId);
        validateLimit(limit);
        ensureUserExists(userId);
        return itemDao.findDiscoveryForUser(userId, limit);
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

    private void validateRecommendationId(long recommendationId) {
        if (recommendationId <= 0) {
            throw new InvalidUserInputException("Recommendation ID must be positive.");
        }
    }
}
