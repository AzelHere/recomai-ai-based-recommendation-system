package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.AdminAnalyticsDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.AdminDashboardSummary;
import com.guvi.recommendationsystem.model.AdminRecommendationInsights;
import org.springframework.stereotype.Service;

@Service
public class AdminAnalyticsService {
    private static final int MAX_INSIGHT_ROWS = 20;
    private final AdminAnalyticsDao analyticsDao;

    public AdminAnalyticsService(AdminAnalyticsDao analyticsDao) {
        this.analyticsDao = analyticsDao;
    }

    public AdminDashboardSummary getSummary() {
        return analyticsDao.getSummary();
    }

    public AdminRecommendationInsights getInsights(int limit) {
        if (limit < 1 || limit > MAX_INSIGHT_ROWS) {
            throw new InvalidUserInputException("Insight limit must be between 1 and 20.");
        }
        return analyticsDao.getRecommendationInsights(limit);
    }
}
