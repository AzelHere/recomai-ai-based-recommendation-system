package com.guvi.recommendationsystem.model;

import java.util.List;

public record InterestProfile(
        List<InterestInsight> current,
        List<InterestInsight> growing,
        List<InterestInsight> declining,
        List<InterestInsight> exploreNext) {
    public InterestProfile {
        current = current == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(current));
        growing = growing == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(growing));
        declining = declining == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(declining));
        exploreNext = exploreNext == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(exploreNext));
    }

}
