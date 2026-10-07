package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
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

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(recommendationDao, userDao, recommendationEngine);
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
        when(recommendationEngine.recommend(List.of(candidate), 3)).thenReturn(List.of(draft));
        when(recommendationDao.createBatch(9L, "content-score-v1")).thenReturn(31L);
        when(recommendationDao.save(9L, 31L, draft)).thenReturn(saved);

        List<PersonalizedRecommendation> results = service.generateForUser(9L, 3);

        assertThat(results).containsExactly(saved);
        verify(recommendationDao).createBatch(9L, "content-score-v1");
        verify(recommendationDao).save(9L, 31L, draft);
    }

    @Test
    void rejectsInvalidRecommendationCountBeforeDatabaseAccess() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.generateForUser(9L, 51));
        verifyNoInteractions(userDao, recommendationDao, recommendationEngine);
    }
}
