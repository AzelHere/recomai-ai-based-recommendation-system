package com.guvi.recommendationsystem.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Applies recency decay and balanced positive/negative evidence to one topic profile. */
@Component
public class TopicPreferenceScorer {
    public TopicSignal apply(double positive, double negative, double previousPreference, long interactionCount,
                             LocalDateTime lastInteractedAt, double signal, LocalDateTime now, int halfLifeDays) {
        double decay = lastInteractedAt == null ? 1.0
                : Math.pow(0.5, Math.max(0, ChronoUnit.HOURS.between(lastInteractedAt, now))
                        / (24.0 * Math.max(1, halfLifeDays)));
        double updatedPositive = positive * decay + Math.max(0, signal);
        double updatedNegative = negative * decay + Math.max(0, -signal);
        long count = interactionCount + 1;
        double preference = (updatedPositive - updatedNegative) / (updatedPositive + updatedNegative + 1.0);
        double confidence = 1.0 - Math.exp(-count / 3.0);
        String trend = Math.abs(preference - previousPreference) < 0.02 ? "STABLE"
                : preference > previousPreference ? "UP" : "DOWN";
        return new TopicSignal(updatedPositive, updatedNegative, preference, confidence, count, trend);
    }

    public double decay(double score, LocalDateTime lastInteractedAt, LocalDateTime now, int halfLifeDays) {
        if (lastInteractedAt == null) return score;
        long elapsedHours = Math.max(0, ChronoUnit.HOURS.between(lastInteractedAt, now));
        return score * Math.pow(0.5, elapsedHours / (24.0 * Math.max(1, halfLifeDays)));
    }

    public record TopicSignal(double positiveScore, double negativeScore, double preferenceScore,
                              double confidence, long interactionCount, String trend) {
    }
}
