package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record OnboardingStatus(
        boolean completed,
        OnboardingPurpose purpose,
        long categoryId,
        LocalDateTime completedAt) {
}
