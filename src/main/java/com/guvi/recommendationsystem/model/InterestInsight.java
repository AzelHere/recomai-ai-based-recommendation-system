package com.guvi.recommendationsystem.model;

public record InterestInsight(
        long interestId,
        String topic,
        String niche,
        int strengthPercent,
        String trend,
        String confidence,
        String evidence) {
}
