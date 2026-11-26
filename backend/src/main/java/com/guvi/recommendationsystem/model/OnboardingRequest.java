package com.guvi.recommendationsystem.model;

import java.util.List;

public record OnboardingRequest(OnboardingPurpose purpose, long categoryId, List<Long> interestIds) {
    public OnboardingRequest {
        interestIds = interestIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(interestIds));
    }

}
