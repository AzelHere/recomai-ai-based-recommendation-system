package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.InteractionDao;
import com.guvi.recommendationsystem.dao.CategoryDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.dao.UserPreferenceDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.CategoryNotFoundException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.InteractionType;
import com.guvi.recommendationsystem.model.ItemType;
import com.guvi.recommendationsystem.model.TrackInteractionRequest;
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

    private UserPreferenceService preferenceService;
    private InteractionService interactionService;

    @BeforeEach
    void setUp() {
        preferenceService = new UserPreferenceService(userDao, userPreferenceDao, categoryDao);
        interactionService = new InteractionService(interactionDao, userDao, itemDao);
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
    void interactionServiceRecordsEventsForActiveItems() {
        LocalDateTime now = LocalDateTime.now();
        CatalogItem item = new CatalogItem(
                6L, 4L, "Article", null, ItemType.CONTENT, null, null, true, now, now);
        Interaction interaction = new Interaction(12L, 3L, 6L, InteractionType.SAVE, now);
        when(userDao.existsById(3L)).thenReturn(true);
        when(itemDao.findById(6L)).thenReturn(Optional.of(item));
        when(interactionDao.insert(3L, 6L, InteractionType.SAVE)).thenReturn(interaction);

        Interaction result = interactionService.track(3L, new TrackInteractionRequest(6L, InteractionType.SAVE));

        assertThat(result.interactionId()).isEqualTo(12L);
        verify(interactionDao).insert(3L, 6L, InteractionType.SAVE);
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
}
