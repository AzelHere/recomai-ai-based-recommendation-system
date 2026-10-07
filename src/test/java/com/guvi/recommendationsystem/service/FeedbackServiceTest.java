package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.FeedbackDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.DuplicateCatalogEntryException;
import com.guvi.recommendationsystem.exception.FeedbackNotFoundException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.Feedback;
import com.guvi.recommendationsystem.model.FeedbackRequest;
import com.guvi.recommendationsystem.model.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {
    @Mock private FeedbackDao feedbackDao;
    @Mock private UserDao userDao;
    @Mock private ItemDao itemDao;
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackService(feedbackDao, userDao, itemDao);
    }

    @Test
    void createsFeedbackAndNormalizesOptionalComment() {
        when(userDao.existsById(5L)).thenReturn(true);
        when(itemDao.findById(8L)).thenReturn(Optional.of(item(true)));
        Feedback created = new Feedback(11L, 5L, 8L, 5, "Helpful", LocalDateTime.now(), LocalDateTime.now());
        when(feedbackDao.insert(5L, 8L, 5, "Helpful")).thenReturn(created);

        Feedback result = service.create(5L, new FeedbackRequest(8L, 5, " Helpful "));

        assertThat(result).isEqualTo(created);
        verify(feedbackDao).insert(5L, 8L, 5, "Helpful");
    }

    @Test
    void rejectsRatingsBelowOneOrAboveFiveBeforeDatabaseAccess() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.create(5L, new FeedbackRequest(8L, 0, null)));
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.create(5L, new FeedbackRequest(8L, 6, null)));
        verifyNoInteractions(feedbackDao, userDao, itemDao);
    }

    @Test
    void reportsDuplicateFeedbackAsAConflict() {
        when(userDao.existsById(5L)).thenReturn(true);
        when(itemDao.findById(8L)).thenReturn(Optional.of(item(true)));
        when(feedbackDao.insert(5L, 8L, 4, null)).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatExceptionOfType(DuplicateCatalogEntryException.class)
                .isThrownBy(() -> service.create(5L, new FeedbackRequest(8L, 4, null)));
    }

    @Test
    void updatesExistingFeedbackAndReturnsThePersistedValue() {
        LocalDateTime now = LocalDateTime.now();
        Feedback updated = new Feedback(11L, 5L, 8L, 1, null, now, now);
        when(userDao.existsById(5L)).thenReturn(true);
        when(itemDao.findById(8L)).thenReturn(Optional.of(item(true)));
        when(feedbackDao.update(5L, 8L, 1, null)).thenReturn(true);
        when(feedbackDao.findByUserAndItem(5L, 8L)).thenReturn(Optional.of(updated));

        assertThat(service.update(5L, 8L, new FeedbackRequest(null, 1, null))).isEqualTo(updated);
    }

    @Test
    void returnsNotFoundWhenThereIsNoExistingFeedbackToUpdate() {
        when(userDao.existsById(5L)).thenReturn(true);
        when(itemDao.findById(8L)).thenReturn(Optional.of(item(true)));
        when(feedbackDao.update(5L, 8L, 3, null)).thenReturn(false);

        assertThatExceptionOfType(FeedbackNotFoundException.class)
                .isThrownBy(() -> service.update(5L, 8L, new FeedbackRequest(null, 3, null)));
    }

    private CatalogItem item(boolean active) {
        LocalDateTime now = LocalDateTime.now();
        return new CatalogItem(8L, 2L, "Course", null, ItemType.COURSE,
                "https://example.com/course", null, active, now, now);
    }
}
