package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.ItemType;
import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import com.guvi.recommendationsystem.recommendation.RecommendationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock
    private RecommendationDao recommendationDao;
    @Mock
    private UserDao userDao;
    @Mock
    private RecommendationEngine recommendationEngine;
    @Mock
    private ItemDao itemDao;

    @Mock private com.guvi.recommendationsystem.dao.UserPreferenceDao preferences;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(recommendationDao, userDao, recommendationEngine, itemDao, preferences);
    }

    @Test
    void generateForUserLoadsSignalsRanksAndPersistsResults() {
        RecommendationCandidate candidate = new RecommendationCandidate(
                7L, "Design guide", null, null, null, null, null, null);
        RecommendationDraft draft = new RecommendationDraft(
                7L, "Design guide", new BigDecimal("12.5000"), "Recommended because it matches your interests.");
        PersonalizedRecommendation saved = new PersonalizedRecommendation(
                22L, 31L, 7L, "Design guide", draft.score(), draft.reason(), LocalDateTime.now());
        when(userDao.existsById(9L)).thenReturn(true);
        when(recommendationDao.findCandidates(9L)).thenReturn(List.of(candidate));
        when(preferences.lockExploration(9L)).thenReturn(50);
        when(recommendationEngine.recommend(List.of(candidate), 3, 50)).thenReturn(List.of(draft));
        when(recommendationDao.createBatch(9L, "hybrid-metadata-v4")).thenReturn(31L);
        when(recommendationDao.save(9L, 31L, draft)).thenReturn(saved);

        List<PersonalizedRecommendation> results = service.generateForUser(9L, 3);

        assertThat(results).containsExactly(saved);
        verify(recommendationDao).createBatch(9L, "hybrid-metadata-v4");
        verify(recommendationDao).save(9L, 31L, draft);
    }

    @Test
    void rejectsInvalidExplorationBeforeWriting() {
        for (String value : List.of("-1", "101", "49.5")) {
            assertThatExceptionOfType(InvalidUserInputException.class)
                    .isThrownBy(() -> service.updateExploration(9L, new BigDecimal(value)));
        }
        verifyNoInteractions(preferences, recommendationDao);
    }

    @Test
    void rejectsInvalidRecommendationCountBeforeDatabaseAccess() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.generateForUser(9L, 51));
        verifyNoInteractions(userDao, recommendationDao, recommendationEngine);
    }

    @Test
    void fallbackReturnsGeneralCatalogDiscoveryWithoutRunningPersonalizedRanker() {
        CatalogItem editorPick = new CatalogItem(12, 4, "Editor choice", null, ItemType.ARTICLE,
                "https://example.com", null, List.of(), com.guvi.recommendationsystem.model.ItemDifficulty.ALL_LEVELS,
                null, BigDecimal.valueOf(0.8), null, true, LocalDateTime.now(), LocalDateTime.now(), true);
        when(userDao.existsById(9L)).thenReturn(true);
        when(itemDao.findDiscoveryForUser(9L, 5)).thenReturn(List.of(editorPick));

        assertThat(service.getCatalogDiscoveryForUser(9L, 5)).containsExactly(editorPick);
        verifyNoInteractions(recommendationDao, recommendationEngine);
    }
}
