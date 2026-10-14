package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.AdminSettingsDao;
import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.AdminSettingsUpdateRequest;
import com.guvi.recommendationsystem.model.AdminSystemSettings;
import com.guvi.recommendationsystem.recommendation.ScoringRecommendationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {
    @Mock private AdminSettingsDao settingsDao;
    @Mock private RecommendationDao recommendationDao;
    @Mock private UserDao userDao;
    private ScoringRecommendationEngine engine;
    private AdminSettingsService service;

    @BeforeEach void setUp() {
        engine = new ScoringRecommendationEngine();
        service = new AdminSettingsService(settingsDao, recommendationDao, userDao, engine);
    }

    @Test void updatePersistsAndAppliesValidatedShares() {
        AdminSystemSettings saved = new AdminSystemSettings(new BigDecimal("0.5000"), new BigDecimal("0.3000"),
                new BigDecimal("0.2000"), LocalDateTime.now());
        when(settingsDao.getSettings()).thenReturn(saved);

        service.update(new AdminSettingsUpdateRequest(new BigDecimal("0.5"), new BigDecimal("0.3"),
                new BigDecimal("0.2")), 17);

        verify(settingsDao).save("familiar", new BigDecimal("0.5000"), 17);
        verify(settingsDao).save("related", new BigDecimal("0.3000"), 17);
        verify(settingsDao).save("discovery", new BigDecimal("0.2000"), 17);
        assertEquals(0.5, engine.publicMix().get("familiar"));
        assertEquals(0.3, engine.publicMix().get("related"));
    }

    @Test void rejectsSharesThatDoNotSumToOneBeforeWriting() {
        assertThrows(InvalidUserInputException.class, () -> service.update(
                new AdminSettingsUpdateRequest(new BigDecimal("0.5"), new BigDecimal("0.3"),
                        new BigDecimal("0.3")), 17));
        verifyNoInteractions(settingsDao);
    }

    @Test void rejectsMissingAdministratorBeforeWriting() {
        assertThrows(InvalidUserInputException.class, () -> service.update(
                new AdminSettingsUpdateRequest(new BigDecimal("0.5"), new BigDecimal("0.3"),
                        new BigDecimal("0.2")), 0));
        verifyNoInteractions(settingsDao);
    }

    @Test void previewRequiresAnExistingSampleUser() {
        when(userDao.existsById(404)).thenReturn(false);
        assertThrows(UserNotFoundException.class, () -> service.preview(
                new com.guvi.recommendationsystem.model.AdminSettingsPreviewRequest(404, new BigDecimal("0.5"),
                        new BigDecimal("0.3"), new BigDecimal("0.2"))));
        verifyNoInteractions(recommendationDao);
    }
}
