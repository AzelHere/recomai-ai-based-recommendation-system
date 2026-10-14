package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.InterestDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.dao.UserInterestDao;
import com.guvi.recommendationsystem.exception.InterestNotFoundException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.UserInterest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class UserInterestService {
    private static final BigDecimal MAX_WEIGHT = BigDecimal.ONE;
    private final UserDao userDao;
    private final InterestDao interestDao;
    private final UserInterestDao userInterestDao;

    public UserInterestService(UserDao userDao, InterestDao interestDao, UserInterestDao userInterestDao) {
        this.userDao = userDao;
        this.interestDao = interestDao;
        this.userInterestDao = userInterestDao;
    }

    public List<UserInterest> getForUser(long userId) {
        validateUserId(userId);
        ensureUserExists(userId);
        return userInterestDao.findByUserId(userId);
    }

    public void select(long userId, long interestId, BigDecimal weight) {
        validateUserId(userId);
        validateInterestId(interestId);
        if (weight == null || weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(MAX_WEIGHT) > 0) {
            throw new InvalidUserInputException("Interest weight must be between 0 and 1.");
        }
        ensureUserExists(userId);
        if (interestDao.findById(interestId).filter(interest -> interest.active()).isEmpty()) {
            throw new InterestNotFoundException("An active interest with that ID was not found.");
        }
        userInterestDao.save(userId, interestId, weight);
    }

    public void remove(long userId, long interestId) {
        validateUserId(userId);
        validateInterestId(interestId);
        ensureUserExists(userId);
        userInterestDao.delete(userId, interestId);
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

    private void validateInterestId(long interestId) {
        if (interestId <= 0) {
            throw new InvalidUserInputException("Interest ID must be positive.");
        }
    }
}
