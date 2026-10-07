package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.FeedbackDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.DuplicateCatalogEntryException;
import com.guvi.recommendationsystem.exception.FeedbackNotFoundException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.Feedback;
import com.guvi.recommendationsystem.model.FeedbackRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeedbackService {
    private static final int MAX_COMMENT_LENGTH = 1000;
    private final FeedbackDao feedbackDao;
    private final UserDao userDao;
    private final ItemDao itemDao;

    public FeedbackService(FeedbackDao feedbackDao, UserDao userDao, ItemDao itemDao) {
        this.feedbackDao = feedbackDao;
        this.userDao = userDao;
        this.itemDao = itemDao;
    }

    public List<Feedback> getForUser(long userId) {
        validateUserId(userId);
        ensureUserExists(userId);
        return feedbackDao.findByUserId(userId);
    }

    public Feedback create(long userId, FeedbackRequest request) {
        validateUserId(userId);
        if (request == null || request.itemId() == null) {
            throw new InvalidUserInputException("Item ID is required.");
        }
        long itemId = request.itemId();
        validateItemId(itemId);
        FeedbackRequest validated = validate(request);
        if (validated.itemId() != null && validated.itemId() != itemId) {
            throw new InvalidUserInputException("Item ID in the request must match the URL.");
        }
        ensureUserExists(userId);
        ensureItemIsActive(itemId);
        try {
            return feedbackDao.insert(userId, itemId, validated.rating(), validated.comment());
        } catch (DuplicateKeyException exception) {
            throw new DuplicateCatalogEntryException("You have already rated this item. Use PUT to update it.");
        }
    }

    public Feedback update(long userId, long itemId, FeedbackRequest request) {
        validateUserId(userId);
        validateItemId(itemId);
        FeedbackRequest validated = validate(request);
        ensureUserExists(userId);
        ensureItemIsActive(itemId);
        if (!feedbackDao.update(userId, itemId, validated.rating(), validated.comment())) {
            throw new FeedbackNotFoundException("Feedback for this item was not found.");
        }
        return feedbackDao.findByUserAndItem(userId, itemId)
                .orElseThrow(() -> new FeedbackNotFoundException("Feedback for this item was not found."));
    }

    private FeedbackRequest validate(FeedbackRequest request) {
        if (request == null || request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new InvalidUserInputException("Rating must be between 1 and 5.");
        }
        String comment = request.comment() == null || request.comment().isBlank()
                ? null : request.comment().trim();
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new InvalidUserInputException("Feedback comment must be 1000 characters or fewer.");
        }
        return new FeedbackRequest(request.itemId(), request.rating(), comment);
    }

    private void ensureItemIsActive(long itemId) {
        if (itemDao.findById(itemId).filter(CatalogItem::active).isEmpty()) {
            throw new ItemNotFoundException("An active item with that ID was not found.");
        }
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

    private void validateItemId(long itemId) {
        if (itemId <= 0) {
            throw new InvalidUserInputException("Item ID must be positive.");
        }
    }
}
