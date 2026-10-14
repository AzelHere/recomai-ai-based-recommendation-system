package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.InteractionDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.dao.UserTopicPreferenceDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.InteractionType;
import com.guvi.recommendationsystem.model.InteractionWriteResult;
import com.guvi.recommendationsystem.model.SearchInteractionRequest;
import com.guvi.recommendationsystem.model.TrackInteractionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InteractionService {
    private static final int MAX_HISTORY_SIZE = 100;

    private final InteractionDao interactionDao;
    private final UserDao userDao;
    private final ItemDao itemDao;
    private final UserTopicPreferenceDao topicPreferenceDao;
    private final InteractionSignalWeights signalWeights;
    private final RecommendationDao recommendationDao;

    public InteractionService(InteractionDao interactionDao, UserDao userDao, ItemDao itemDao,
                              UserTopicPreferenceDao topicPreferenceDao, InteractionSignalWeights signalWeights,
                              RecommendationDao recommendationDao) {
        this.interactionDao = interactionDao;
        this.userDao = userDao;
        this.itemDao = itemDao;
        this.topicPreferenceDao = topicPreferenceDao;
        this.signalWeights = signalWeights;
        this.recommendationDao = recommendationDao;
    }

    @Transactional
    public Interaction track(long userId, TrackInteractionRequest request) {
        validateUserId(userId);
        if (request == null || request.type() == null) {
            throw new InvalidUserInputException("An interaction type is required.");
        }
        if (request.type() == InteractionType.SEARCH) {
            throw new InvalidUserInputException("Use the search event endpoint to record a search.");
        }
        String requestId = validateRequestId(request.requestId());
        validateItemId(request.itemId());
        ensureUserExists(userId);
        ensureItemIsActive(request.itemId());
        InteractionWriteResult result = requestId == null
                ? new InteractionWriteResult(interactionDao.insert(userId, request.itemId(), request.type()), true)
                : interactionDao.insertWithRequestId(userId, request.itemId(), request.type(), requestId);
        if (!result.created() && (!Long.valueOf(request.itemId()).equals(result.interaction().itemId())
                || result.interaction().type() != request.type())) {
            throw new InvalidUserInputException("Interaction request ID was already used for a different action.");
        }
        // Only a newly recorded event updates the inferred profile. Replayed HTTP requests do not.
        if (result.created() && updatesTopicPreference(request.type())) {
            topicPreferenceDao.applyInteraction(userId, request.itemId(), request.type(),
                    signalWeights.weight(request.type()), 45);
        }
        return result.interaction();
    }

    @Transactional
    public Interaction trackSearch(long userId, SearchInteractionRequest request) {
        validateUserId(userId);
        String query = request == null || request.query() == null ? "" : request.query().strip();
        if (query.length() < 2 || query.length() > 120) {
            throw new InvalidUserInputException("Search text must be between 2 and 120 characters.");
        }
        String requestId = validateRequestId(request == null ? null : request.requestId());
        if (!userDao.existsById(userId)) {
            throw new UserNotFoundException("User was not found.");
        }
        InteractionWriteResult result = requestId == null
                ? new InteractionWriteResult(interactionDao.insertSearch(userId, query), true)
                : interactionDao.insertSearchWithRequestId(userId, query, requestId);
        if (!result.created() && (!InteractionType.SEARCH.equals(result.interaction().type())
                || !query.equals(result.interaction().searchQuery()))) {
            throw new InvalidUserInputException("Interaction request ID was already used for a different action.");
        }
        if (result.created()) {
            topicPreferenceDao.applySearch(userId, query, signalWeights.weight(InteractionType.SEARCH), 45);
        }
        return result.interaction();
    }

    public List<Interaction> getRecentForUser(long userId, int limit) {
        validateUserId(userId);
        if (limit < 1 || limit > MAX_HISTORY_SIZE) {
            throw new InvalidUserInputException("History limit must be between 1 and 100.");
        }
        ensureUserExists(userId);
        return interactionDao.findRecentByUser(userId, limit);
    }

    public List<Interaction> getCurrentProgressForUser(long userId) {
        validateUserId(userId);
        ensureUserExists(userId);
        return interactionDao.findCurrentProgressByUser(userId);
    }

    @Transactional
    public void clearHistory(long userId) {
        validateUserId(userId);
        ensureUserExists(userId);
        interactionDao.deleteAllForUser(userId);
        topicPreferenceDao.deleteAllForUser(userId);
        recommendationDao.deleteAllForUser(userId);
    }

    private void ensureUserExists(long userId) {
        if (!userDao.existsById(userId)) {
            throw new UserNotFoundException("User was not found.");
        }
    }

    private void ensureItemIsActive(long itemId) {
        CatalogItem item = itemDao.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("An active item with that ID was not found."));
        if (!item.active()) {
            throw new ItemNotFoundException("An active item with that ID was not found.");
        }
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            throw new InvalidUserInputException("User ID must be positive.");
        }
    }

    private void validateItemId(long itemId) {
        if (itemId <= 0) {
            throw new InvalidUserInputException("Item ID must be positive.");
        }
    }

    private String validateRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) return null;
        try {
            UUID parsed = UUID.fromString(requestId);
            if (!parsed.toString().equalsIgnoreCase(requestId)) throw new IllegalArgumentException();
            return parsed.toString();
        } catch (IllegalArgumentException exception) {
            throw new InvalidUserInputException("Interaction request ID must be a valid UUID.");
        }
    }

    /** Explicit positive item actions score the item directly; passive/negative signals adapt topics. */
    private boolean updatesTopicPreference(InteractionType type) {
        return type == InteractionType.VIEW || type == InteractionType.CLICK
                || type == InteractionType.READ_PROGRESS || type == InteractionType.DISLIKE
                || type == InteractionType.HIDE || type == InteractionType.SKIP || type == InteractionType.MORE_LIKE;
    }
}
