package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.AdminDashboardSummary;
import com.guvi.recommendationsystem.model.AdminRecommendationInsights;
import com.guvi.recommendationsystem.model.RecommendationEngagementMetrics;

public interface AdminAnalyticsDao {
    AdminDashboardSummary getSummary();

    AdminRecommendationInsights getRecommendationInsights(int limit);

    RecommendationEngagementMetrics getRecommendationEngagement();
}
