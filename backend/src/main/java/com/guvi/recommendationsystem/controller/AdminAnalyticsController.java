package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.AdminDashboardSummary;
import com.guvi.recommendationsystem.model.AdminRecommendationInsights;
import com.guvi.recommendationsystem.service.AdminAnalyticsService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {
    private final AdminAnalyticsService analyticsService;
    private final SessionAccess sessionAccess;

    public AdminAnalyticsController(AdminAnalyticsService analyticsService, SessionAccess sessionAccess) {
        this.analyticsService = analyticsService;
        this.sessionAccess = sessionAccess;
    }

    @GetMapping("/summary")
    public AdminDashboardSummary getSummary(HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return analyticsService.getSummary();
    }

    @GetMapping("/insights")
    public AdminRecommendationInsights getInsights(
            @RequestParam(defaultValue = "5") int limit, HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return analyticsService.getInsights(limit);
    }
}
