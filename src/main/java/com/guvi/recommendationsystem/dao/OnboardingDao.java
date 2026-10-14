package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.OnboardingPurpose;
import com.guvi.recommendationsystem.model.OnboardingStatus;
import com.guvi.recommendationsystem.model.NicheOption;

import java.util.List;
import java.util.Optional;

public interface OnboardingDao {
    List<NicheOption> findOptions();
    Optional<OnboardingStatus> findStatus(long userId);
    void save(long userId, OnboardingPurpose purpose, long categoryId, List<Long> interestIds);
    boolean isSupportedNiche(long categoryId);
    boolean areTopicsInNiche(long categoryId, List<Long> interestIds);
}
