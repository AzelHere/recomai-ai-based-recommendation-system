package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.OnboardingDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.OnboardingPurpose;
import com.guvi.recommendationsystem.model.OnboardingRequest;
import com.guvi.recommendationsystem.model.OnboardingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {
    @Mock private OnboardingDao onboardingDao;
    @Mock private UserDao userDao;
    private OnboardingService service;

    @BeforeEach
    void setUp() {
        service = new OnboardingService(onboardingDao, userDao);
        when(userDao.existsById(7L)).thenReturn(true);
    }

    @Test
    void acceptsLightweightOnboardingAndStoresUpToThreeTopics() {
        var status = new OnboardingStatus(true, OnboardingPurpose.LEARN, 3L, LocalDateTime.now());
        when(onboardingDao.isSupportedNiche(3L)).thenReturn(true);
        when(onboardingDao.areTopicsInNiche(3L, List.of(11L, 12L, 13L))).thenReturn(true);
        when(onboardingDao.findStatus(7L)).thenReturn(Optional.of(status));

        OnboardingStatus result = service.save(7L,
                new OnboardingRequest(OnboardingPurpose.LEARN, 3L, List.of(11L, 12L, 13L)));

        assertThat(result).isEqualTo(status);
        verify(onboardingDao).save(7L, OnboardingPurpose.LEARN, 3L, List.of(11L, 12L, 13L));
    }

    @Test
    void allowsSkippingTopics() {
        when(onboardingDao.isSupportedNiche(3L)).thenReturn(true);
        when(onboardingDao.areTopicsInNiche(3L, List.of())).thenReturn(true);
        when(onboardingDao.findStatus(7L)).thenReturn(Optional.of(
                new OnboardingStatus(true, OnboardingPurpose.EXPLORE, 3L, LocalDateTime.now())));

        service.save(7L, new OnboardingRequest(OnboardingPurpose.EXPLORE, 3L, null));

        verify(onboardingDao).save(7L, OnboardingPurpose.EXPLORE, 3L, List.of());
    }

    @Test
    void rejectsMoreThanThreeTopicsAndTopicsOutsideTheChosenNiche() {
        when(onboardingDao.isSupportedNiche(3L)).thenReturn(true);
        assertThatExceptionOfType(InvalidUserInputException.class).isThrownBy(() -> service.save(7L,
                new OnboardingRequest(OnboardingPurpose.CAREER, 3L, List.of(1L, 2L, 3L, 4L))));

        when(onboardingDao.areTopicsInNiche(3L, List.of(11L))).thenReturn(false);
        assertThatExceptionOfType(InvalidUserInputException.class).isThrownBy(() -> service.save(7L,
                new OnboardingRequest(OnboardingPurpose.CAREER, 3L, List.of(11L))));
    }
}
