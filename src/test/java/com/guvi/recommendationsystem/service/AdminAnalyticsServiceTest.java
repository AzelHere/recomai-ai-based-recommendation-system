package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.AdminAnalyticsDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminAnalyticsServiceTest {
    @Test
    void rejectsAnUnboundedOrEmptyInsightRequestBeforeQuerying() {
        AdminAnalyticsDao dao = mock(AdminAnalyticsDao.class);
        AdminAnalyticsService service = new AdminAnalyticsService(dao);

        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.getInsights(0));
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.getInsights(21));
        verifyNoInteractions(dao);
    }
}
