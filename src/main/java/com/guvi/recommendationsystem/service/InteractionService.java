package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.InteractionDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.TrackInteractionRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InteractionService {
    private static final int MAX_HISTORY_SIZE = 100;

    private final InteractionDao interactionDao;
    private final UserDao userDao;
    private final ItemDao itemDao;

    public InteractionService(InteractionDao interactionDao, UserDao userDao, ItemDao itemDao) {
        this.interactionDao = interactionDao;
        this.userDao = userDao;
        this.itemDao = itemDao;
    }

    public Interaction track(long userId, TrackInteractionRequest request) {
        validateUserId(userId);
        if (request == null || request.type() == null) {
            throw new InvalidUserInputException("An interaction type is required.");
        }
        validateItemId(request.itemId());
        ensureUserExists(userId);
        ensureItemIsActive(request.itemId());
        return interactionDao.insert(userId, request.itemId(), request.type());
    }

    public List<Interaction> getRecentForUser(long userId, int limit) {
        validateUserId(userId);
        if (limit < 1 || limit > MAX_HISTORY_SIZE) {
            throw new InvalidUserInputException("History limit must be between 1 and 100.");
        }
        ensureUserExists(userId);
        return interactionDao.findRecentByUser(userId, limit);
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
}
