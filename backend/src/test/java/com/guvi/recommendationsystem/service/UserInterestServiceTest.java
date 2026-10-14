package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.InterestDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.dao.UserInterestDao;
import com.guvi.recommendationsystem.exception.InterestNotFoundException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.Interest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInterestServiceTest {
    @Mock private UserDao userDao;
    @Mock private InterestDao interestDao;
    @Mock private UserInterestDao userInterestDao;
    private UserInterestService service;

    @BeforeEach
    void setUp() {
        service = new UserInterestService(userDao, interestDao, userInterestDao);
    }

    @Test
    void savesAnActiveInterestAtTheRequestedWeight() {
        when(userDao.existsById(4L)).thenReturn(true);
        when(interestDao.findById(9L)).thenReturn(Optional.of(interest(true)));

        service.select(4L, 9L, new BigDecimal("0.75"));

        verify(userInterestDao).save(4L, 9L, new BigDecimal("0.75"));
    }

    @Test
    void rejectsAnInactiveInterest() {
        when(userDao.existsById(4L)).thenReturn(true);
        when(interestDao.findById(9L)).thenReturn(Optional.of(interest(false)));

        assertThatExceptionOfType(InterestNotFoundException.class)
                .isThrownBy(() -> service.select(4L, 9L, BigDecimal.ONE));
        verifyNoInteractions(userInterestDao);
    }

    @Test
    void rejectsWeightsOutsideTheDatabaseRange() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> service.select(4L, 9L, new BigDecimal("1.01")));
        verifyNoInteractions(userDao, interestDao, userInterestDao);
    }

    private Interest interest(boolean active) {
        LocalDateTime now = LocalDateTime.now();
        return new Interest(9L, "Java", active, now, now);
    }
}
