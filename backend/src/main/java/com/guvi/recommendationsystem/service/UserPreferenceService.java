package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.dao.UserPreferenceDao;
import com.guvi.recommendationsystem.dao.CategoryDao;
import com.guvi.recommendationsystem.exception.CategoryNotFoundException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.UserPreference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class UserPreferenceService {
    private static final BigDecimal MIN_WEIGHT = BigDecimal.ZERO;
    private static final BigDecimal MAX_WEIGHT = BigDecimal.ONE;

    private final UserDao userDao;
    private final UserPreferenceDao preferenceDao;
    private final CategoryDao categoryDao;

    public UserPreferenceService(UserDao userDao, UserPreferenceDao preferenceDao, CategoryDao categoryDao) {
        this.userDao = userDao;
        this.preferenceDao = preferenceDao;
        this.categoryDao = categoryDao;
    }

    public List<UserPreference> getPreferences(long userId) {
        validateUserId(userId);
        ensureUserExists(userId);
        return preferenceDao.findByUserId(userId);
    }

    public void setPreference(long userId, long categoryId, BigDecimal weight) {
        validateUserId(userId);
        if (categoryId <= 0) {
            throw new InvalidUserInputException("Category ID must be positive.");
        }
        if (weight == null || weight.compareTo(MIN_WEIGHT) < 0 || weight.compareTo(MAX_WEIGHT) > 0) {
            throw new InvalidUserInputException("Preference weight must be between 0 and 1.");
        }
        ensureUserExists(userId);
        if (categoryDao.findById(categoryId).filter(category -> category.active()).isEmpty()) {
            throw new CategoryNotFoundException("An active category with that ID was not found.");
        }
        preferenceDao.save(userId, categoryId, weight);
    }

    private void validateUserId(long userId) {
        if (userId <= 0) {
            throw new InvalidUserInputException("User ID must be positive.");
        }
    }

    private void ensureUserExists(long userId) {
        if (!userDao.existsById(userId)) {
            throw new UserNotFoundException("User was not found.");
        }
    }
}
