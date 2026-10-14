package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.InteractionDao;
import com.guvi.recommendationsystem.dao.CategoryDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.dao.UserTopicPreferenceDao;
import com.guvi.recommendationsystem.dao.UserPreferenceDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.CategoryNotFoundException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.InteractionType;
import com.guvi.recommendationsystem.model.ItemType;
import com.guvi.recommendationsystem.model.SearchInteractionRequest;
import com.guvi.recommendationsystem.model.TrackInteractionRequest;
import com.guvi.recommendationsystem.model.InteractionWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSignalServiceTest {
    @Mock
    private UserDao userDao;
    @Mock
    private UserPreferenceDao userPreferenceDao;
    @Mock
    private CategoryDao categoryDao;
    @Mock
    private ItemDao itemDao;
    @Mock
    private InteractionDao interactionDao;
    @Mock
    private UserTopicPreferenceDao userTopicPreferenceDao;
    @Mock
    private InteractionSignalWeights signalWeights;
    @Mock
    private RecommendationDao recommendationDao;

    private UserPreferenceService preferenceService;
    private InteractionService interactionService;

    @BeforeEach
    void setUp() {
        preferenceService = new UserPreferenceService(userDao, userPreferenceDao, categoryDao);
        interactionService = new InteractionService(interactionDao, userDao, itemDao, userTopicPreferenceDao,
                signalWeights, recommendationDao);
    }

    @Test
    void preferenceServiceRejectsWeightsOutsideZeroToOne() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> preferenceService.setPreference(3L, 4L, null));
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> preferenceService.setPreference(3L, 4L, new java.math.BigDecimal("1.5")));
        verifyNoInteractions(userPreferenceDao);
    }

    @Test
    void preferenceServiceSavesAValidWeightForAnExistingUser() {
        when(userDao.existsById(3L)).thenReturn(true);
        when(categoryDao.findById(4L)).thenReturn(Optional.of(
                new com.guvi.recommendationsystem.model.Category(4L, "Books", null, true, LocalDateTime.now())));

        preferenceService.setPreference(3L, 4L, new java.math.BigDecimal("0.75"));

        verify(userPreferenceDao).save(3L, 4L, new java.math.BigDecimal("0.75"));
    }

    @Test
    void preferenceServiceRequiresAnActiveCategory() {
        when(userDao.existsById(3L)).thenReturn(true);
        when(categoryDao.findById(4L)).thenReturn(Optional.of(
                new com.guvi.recommendationsystem.model.Category(4L, "Books", null, false, LocalDateTime.now())));

        assertThatExceptionOfType(CategoryNotFoundException.class)
                .isThrownBy(() -> preferenceService.setPreference(3L, 4L, new java.math.BigDecimal("0.5")));
        verifyNoInteractions(userPreferenceDao);
    }

    @Test
    void passiveActivityAdaptsTopicPreferences() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem item = new CatalogItem(
                6L, 4L, "Article", null, ItemType.CONTENT, null, null, true, now, now);
        Interaction interaction = new Interaction(12L, 3L, 6L, InteractionType.VIEW, now);
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(item));
        when(interactionDao.insert(3L, 6L, InteractionType.VIEW)).thenReturn(interaction);
        when(signalWeights.weight(InteractionType.VIEW)).thenReturn(0.15);

        Interaction result = interactionService.track(3L, new TrackInteractionRequest(6L, InteractionType.VIEW));

        assertThat(result.interactionId()).isEqualTo(12L);
        verify(interactionDao).insert(3L, 6L, InteractionType.VIEW);
        verify(userTopicPreferenceDao).applyInteraction(3L, 6L, InteractionType.VIEW, 0.15, 45);
    }

    @Test
    void interactionServiceAcceptsLikeAsAPositiveSignalType() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem item = new CatalogItem(
                6L, 4L, "Article", null, ItemType.ARTICLE, null, null, true, now, now);
        Interaction interaction = new Interaction(13L, 3L, 6L, InteractionType.LIKE, now);
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(item));
        when(interactionDao.insert(3L, 6L, InteractionType.LIKE)).thenReturn(interaction);
        assertThat(interactionService.track(3L,
                new TrackInteractionRequest(6L, InteractionType.LIKE))).isEqualTo(interaction);
        verifyNoInteractions(userTopicPreferenceDao);
    }

    @Test
    void negativeFeedbackAdaptsRelatedTopicPreferencesInsteadOfAddingADuplicateItemPenalty() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem item = new CatalogItem(6L, 4L, "Article", null, ItemType.ARTICLE, null, null, true, now, now);
        Interaction interaction = new Interaction(13L, 3L, 6L, InteractionType.DISLIKE, now);
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(item));
        when(interactionDao.insert(3L, 6L, InteractionType.DISLIKE)).thenReturn(interaction);
        when(signalWeights.weight(InteractionType.DISLIKE)).thenReturn(-1.25);

        assertThat(interactionService.track(3L,
                new TrackInteractionRequest(6L, InteractionType.DISLIKE))).isEqualTo(interaction);

        verify(userTopicPreferenceDao).applyInteraction(3L, 6L, InteractionType.DISLIKE, -1.25, 45);
    }

    @Test
    void moreLikeThisAdaptsTopicsWithoutAlsoScoringTheSameItemDirectly() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem item = new CatalogItem(6L, 4L, "Article", null, ItemType.ARTICLE, null, null, true, now, now);
        Interaction interaction = new Interaction(13L, 3L, 6L, InteractionType.MORE_LIKE, now);
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(item));
        when(interactionDao.insert(3L, 6L, InteractionType.MORE_LIKE)).thenReturn(interaction);
        when(signalWeights.weight(InteractionType.MORE_LIKE)).thenReturn(1.25);

        interactionService.track(3L, new TrackInteractionRequest(6L, InteractionType.MORE_LIKE));

        verify(userTopicPreferenceDao).applyInteraction(3L, 6L, InteractionType.MORE_LIKE, 1.25, 45);
    }

    @Test
    void requestReplayReturnsTheOriginalEventWithoutApplyingItsPreferenceSignalAgain() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem item = new CatalogItem(6L, 4L, "Article", null, ItemType.ARTICLE, null, null, true, now, now);
        Interaction interaction = new Interaction(13L, 3L, 6L, InteractionType.READ_PROGRESS, now);
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(item));
        when(interactionDao.insertWithRequestId(3L, 6L, InteractionType.READ_PROGRESS,
                "52b74c65-12be-4c72-8a92-442809bdf137"))
                .thenReturn(new InteractionWriteResult(interaction, true),
                        new InteractionWriteResult(interaction, false));
        when(signalWeights.weight(InteractionType.READ_PROGRESS)).thenReturn(0.65);
        TrackInteractionRequest request = new TrackInteractionRequest(6L, InteractionType.READ_PROGRESS,
                "52b74c65-12be-4c72-8a92-442809bdf137");

        assertThat(interactionService.track(3L, request)).isEqualTo(interaction);
        assertThat(interactionService.track(3L, request)).isEqualTo(interaction);

        verify(interactionDao, org.mockito.Mockito.times(2)).insertWithRequestId(3L, 6L, InteractionType.READ_PROGRESS,
                "52b74c65-12be-4c72-8a92-442809bdf137");
        verify(userTopicPreferenceDao).applyInteraction(3L, 6L, InteractionType.READ_PROGRESS, 0.65, 45);
    }

    @Test
    void distinctRepeatedProgressEventsRemainLegitimateLearningEvents() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem item = new CatalogItem(6L, 4L, "Article", null, ItemType.ARTICLE, null, null, true, now, now);
        Interaction first = new Interaction(13L, 3L, 6L, InteractionType.READ_PROGRESS, now);
        Interaction second = new Interaction(14L, 3L, 6L, InteractionType.READ_PROGRESS, now.plusSeconds(1));
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(item));
        when(interactionDao.insertWithRequestId(3L, 6L, InteractionType.READ_PROGRESS,
                "52b74c65-12be-4c72-8a92-442809bdf137"))
                .thenReturn(new InteractionWriteResult(first, true));
        when(interactionDao.insertWithRequestId(3L, 6L, InteractionType.READ_PROGRESS,
                "52b74c65-12be-4c72-8a92-442809bdf138"))
                .thenReturn(new InteractionWriteResult(second, true));
        when(signalWeights.weight(InteractionType.READ_PROGRESS)).thenReturn(0.5);

        interactionService.track(3L, new TrackInteractionRequest(6L, InteractionType.READ_PROGRESS,
                "52b74c65-12be-4c72-8a92-442809bdf137"));
        interactionService.track(3L, new TrackInteractionRequest(6L, InteractionType.READ_PROGRESS,
                "52b74c65-12be-4c72-8a92-442809bdf138"));

        verify(userTopicPreferenceDao, org.mockito.Mockito.times(2)).applyInteraction(
                3L, 6L, InteractionType.READ_PROGRESS, 0.5, 45);
    }

    @Test
    void invalidIdempotencyKeyIsRejectedBeforeDatabaseWrites() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> interactionService.track(3L,
                        new TrackInteractionRequest(6L, InteractionType.LIKE, "not-a-uuid")));
        verifyNoInteractions(interactionDao, userTopicPreferenceDao);
    }

    @Test
    void requestIdCannotBeReusedForADifferentAction() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem item = new CatalogItem(6L, 4L, "Article", null, ItemType.ARTICLE, null, null, true, now, now);
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(item));
        when(interactionDao.insertWithRequestId(3L, 6L, InteractionType.LIKE,
                "52b74c65-12be-4c72-8a92-442809bdf137"))
                .thenReturn(new InteractionWriteResult(new Interaction(1L, 3L, 6L,
                        InteractionType.SAVE, now), false));

        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> interactionService.track(3L, new TrackInteractionRequest(6L,
                        InteractionType.LIKE, "52b74c65-12be-4c72-8a92-442809bdf137")));
        verifyNoInteractions(userTopicPreferenceDao);
    }

    @Test
    void searchEventAddsOnlyAWeakTopicSignal() {
        LocalDateTime now = LocalDateTime.now();
        Interaction interaction = new Interaction(14L, 3L, null, InteractionType.SEARCH, now, "cloud computing");
        when(userDao.existsById(3L)).thenReturn(true);
        when(interactionDao.insertSearch(3L, "cloud computing")).thenReturn(interaction);
        when(signalWeights.weight(InteractionType.SEARCH)).thenReturn(0.12);

        assertThat(interactionService.trackSearch(3L, new SearchInteractionRequest("  cloud computing  ")))
                .isEqualTo(interaction);

        verify(interactionDao).insertSearch(3L, "cloud computing");
        verify(userTopicPreferenceDao).applySearch(3L, "cloud computing", 0.12, 45);
    }

    @Test
    void searchEventRejectsBlankOrTooShortQueries() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> interactionService.trackSearch(3L, new SearchInteractionRequest(" ")));
        verifyNoInteractions(interactionDao, userTopicPreferenceDao);
    }

    @Test
    void searchCannotBeRecordedAsAnItemInteraction() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> interactionService.track(3L,
                        new TrackInteractionRequest(6L, InteractionType.SEARCH)));
        verifyNoInteractions(interactionDao, userTopicPreferenceDao);
    }

    @Test
    void interactionServiceRejectsInactiveItemsAndInvalidHistoryLimits() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem inactiveItem = new CatalogItem(
                6L, 4L, "Article", null, ItemType.CONTENT, null, null, false, now, now);
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(inactiveItem));

        assertThatExceptionOfType(ItemNotFoundException.class)
                .isThrownBy(() -> interactionService.track(3L, new TrackInteractionRequest(6L, InteractionType.VIEW)));
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> interactionService.getRecentForUser(3L, 0));
        verifyNoInteractions(interactionDao);
    }

    @Test
    void clearingHistoryRemovesEventsInferredTopicsAndRecommendationBatches() {
        when(userDao.existsById(3L)).thenReturn(true);
        interactionService.clearHistory(3L);
        verify(interactionDao).deleteAllForUser(3L);
        verify(userTopicPreferenceDao).deleteAllForUser(3L);
        verify(recommendationDao).deleteAllForUser(3L);
    }
}
