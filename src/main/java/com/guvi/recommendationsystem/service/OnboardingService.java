package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.OnboardingDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.NicheOption;
import com.guvi.recommendationsystem.model.OnboardingRequest;
import com.guvi.recommendationsystem.model.OnboardingStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OnboardingService {
    private final OnboardingDao onboardingDao;
    private final UserDao userDao;

    public OnboardingService(OnboardingDao onboardingDao, UserDao userDao) {
        this.onboardingDao = onboardingDao;
        this.userDao = userDao;
    }

    public List<NicheOption> getOptions() {
        return onboardingDao.findOptions();
    }

    public OnboardingStatus getStatus(long userId) {
        ensureUserExists(userId);
        return onboardingDao.findStatus(userId).orElse(new OnboardingStatus(false, null, 0, null));
    }

    public OnboardingStatus save(long userId, OnboardingRequest request) {
        ensureUserExists(userId);
        if (request == null || request.purpose() == null || request.categoryId() <= 0) {
            throw new InvalidUserInputException("Choose your goal and one area of interest.");
        }
        List<Long> topics = request.interestIds() == null ? List.of() : request.interestIds().stream().distinct().toList();
        if (topics.size() > 3 || topics.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidUserInputException("Choose up to three valid topics, or skip topics.");
        }
        if (!onboardingDao.isSupportedNiche(request.categoryId())) {
            throw new InvalidUserInputException("Choose one of the five supported areas.");
        }
        if (!onboardingDao.areTopicsInNiche(request.categoryId(), topics)) {
            throw new InvalidUserInputException("Each selected topic must belong to your chosen area.");
        }
        onboardingDao.save(userId, request.purpose(), request.categoryId(), topics);
        return getStatus(userId);
    }

    private void ensureUserExists(long userId) {
        if (userId <= 0 || !userDao.existsById(userId)) {
            throw new UserNotFoundException("User was not found.");
        }
    }
}
