package com.guvi.recommendationsystem.model;

import java.util.List;

public record OnboardingRequest(OnboardingPurpose purpose, long categoryId, List<Long> interestIds) {
}
