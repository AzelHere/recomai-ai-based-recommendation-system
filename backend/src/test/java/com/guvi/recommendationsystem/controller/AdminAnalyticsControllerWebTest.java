package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.AdminDashboardSummary;
import com.guvi.recommendationsystem.model.RecommendationEngagementMetrics;
import com.guvi.recommendationsystem.service.AdminAnalyticsService;
import com.guvi.recommendationsystem.web.ApiExceptionHandler;
import com.guvi.recommendationsystem.web.SessionAccess;
import com.guvi.recommendationsystem.dao.UserDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.OptionalInt;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAnalyticsController.class)
@Import({SessionAccess.class, ApiExceptionHandler.class})
class AdminAnalyticsControllerWebTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean private UserDao userDao;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminAnalyticsService analyticsService;

    @org.junit.jupiter.api.BeforeEach
    void userAuthVersionExistsForAuthenticatedTestSessions() {
        when(userDao.findAuthVersion(21L)).thenReturn(OptionalInt.of(1));
    }

    @Test
    void summaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/summary"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void ordinaryUserCannotReadAdministratorAnalytics() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/summary")
                        .sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "USER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(analyticsService);
    }

    @Test
    void administratorReceivesDatabaseSummary() throws Exception {
        when(analyticsService.getSummary()).thenReturn(new AdminDashboardSummary(
                8, 3, 20, 5, 40, 16, new BigDecimal("4.25")));

        mockMvc.perform(get("/api/admin/analytics/summary")
                        .sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(8))
                .andExpect(jsonPath("$.activeUsersLast30Days").value(3))
                .andExpect(jsonPath("$.averageRating").value(4.25));
    }

    @Test
    void recommendationEngagementIsAdminOnlyAndReportsUnavailableSeparately() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/recommendation-engagement")
                        .sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "USER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(analyticsService);

        when(analyticsService.getRecommendationEngagement()).thenReturn(
                new RecommendationEngagementMetrics(0, 0, BigDecimal.ZERO, false));
        mockMvc.perform(get("/api/admin/analytics/recommendation-engagement")
                        .sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impressions").value(0))
                .andExpect(jsonPath("$.available").value(false));
    }
}
