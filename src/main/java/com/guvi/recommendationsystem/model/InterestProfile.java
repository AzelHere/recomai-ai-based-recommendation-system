package com.guvi.recommendationsystem.model;

import java.util.List;

public record InterestProfile(
        List<InterestInsight> current,
        List<InterestInsight> growing,
        List<InterestInsight> declining,
        List<InterestInsight> exploreNext) {
}
