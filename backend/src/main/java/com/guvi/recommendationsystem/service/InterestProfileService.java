package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.dao.UserTopicPreferenceDao;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.InterestProfile;
import org.springframework.stereotype.Service;

@Service
public class InterestProfileService {
    private final UserDao userDao;
    private final UserTopicPreferenceDao preferenceDao;

    public InterestProfileService(UserDao userDao, UserTopicPreferenceDao preferenceDao) {
        this.userDao = userDao;
        this.preferenceDao = preferenceDao;
    }

    public InterestProfile getForUser(long userId) {
        if (userId <= 0 || !userDao.existsById(userId)) {
            throw new UserNotFoundException("User was not found.");
        }
        return preferenceDao.findProfile(userId);
    }
}
