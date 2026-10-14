package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.UserCollectionDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.ItemDifficulty;
import com.guvi.recommendationsystem.model.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCollectionServiceTest {
    @Mock private UserCollectionDao collectionDao;
    @Mock private UserDao userDao;
    @Mock private ItemDao itemDao;
    private UserCollectionService service;

    @BeforeEach void setUp() { service = new UserCollectionService(collectionDao, userDao, itemDao); }

    @Test void trimsAndPersistsCollectionNames() {
        when(userDao.existsById(4L)).thenReturn(true);
        service.create(4L, " Learn Java ");
        verify(collectionDao).create(4L, "Learn Java");
    }

    @Test void rejectsBlankOrOversizedCollectionNames() {
        when(userDao.existsById(4L)).thenReturn(true);
        assertThatExceptionOfType(InvalidUserInputException.class).isThrownBy(() -> service.create(4L, "  "));
        assertThatExceptionOfType(InvalidUserInputException.class).isThrownBy(() -> service.create(4L, "x".repeat(81)));
        verifyNoInteractions(collectionDao);
    }

    @Test void doesNotAllowChangingAnotherUsersCollection() {
        when(userDao.existsById(4L)).thenReturn(true);
        when(collectionDao.belongsToUser(4L, 9L)).thenReturn(false);
        assertThatExceptionOfType(UserNotFoundException.class).isThrownBy(() -> service.removeItem(4L, 9L, 2L));
        verify(collectionDao, never()).removeItem(anyLong(), anyLong());
    }

    @Test void onlyActiveCatalogItemsCanBeAdded() {
        when(userDao.existsById(4L)).thenReturn(true);
        when(collectionDao.belongsToUser(4L, 9L)).thenReturn(true);
        when(itemDao.findById(2L)).thenReturn(Optional.of(new CatalogItem(2L, 1L, "Guide", "", ItemType.GUIDE,
                "https://example.org", "Example", java.util.List.of(), ItemDifficulty.BEGINNER, 10,
                BigDecimal.ONE, null, false, LocalDateTime.now(), LocalDateTime.now(), false)));
        assertThatExceptionOfType(com.guvi.recommendationsystem.exception.ItemNotFoundException.class)
                .isThrownBy(() -> service.addItem(4L, 9L, 2L));
        verify(collectionDao, never()).addItem(anyLong(), anyLong());
    }
}
