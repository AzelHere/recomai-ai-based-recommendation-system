package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.UserSignalSummaryDao;
import com.guvi.recommendationsystem.model.RecommendationProfile;
import org.springframework.stereotype.Service;

@Service
public class RecommendationProfileService {
    private final UserAccountService accountService;
    private final UserPreferenceService preferenceService;
    private final UserInterestService interestService;
    private final UserSignalSummaryDao signalSummaryDao;

    public RecommendationProfileService(
            UserAccountService accountService,
            UserPreferenceService preferenceService,
            UserInterestService interestService,
            UserSignalSummaryDao signalSummaryDao) {
        this.accountService = accountService;
        this.preferenceService = preferenceService;
        this.interestService = interestService;
        this.signalSummaryDao = signalSummaryDao;
    }

    public RecommendationProfile getForUser(long userId) {
        return new RecommendationProfile(
                accountService.getProfile(userId),
                preferenceService.getPreferences(userId),
                interestService.getForUser(userId),
                signalSummaryDao.countInteractions(userId),
                signalSummaryDao.countFeedback(userId));
    }
}
