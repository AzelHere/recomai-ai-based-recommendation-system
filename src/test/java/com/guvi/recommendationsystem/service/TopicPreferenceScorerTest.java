package com.guvi.recommendationsystem.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TopicPreferenceScorerTest {
    private final TopicPreferenceScorer scorer = new TopicPreferenceScorer();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 7, 12, 0);

    @Test
    void halfLifeReducesOldSignalsWithoutDeletingTheHistory() {
        double recent = scorer.decay(1.0, now.minusDays(1), now, 30);
        double old = scorer.decay(1.0, now.minusDays(30), now, 30);

        assertThat(recent).isGreaterThan(old);
        assertThat(old).isEqualTo(0.5);
    }

    @Test
    void missingOrFutureTimestampsDoNotInventDecay() {
        assertThat(scorer.decay(1.0, null, now, 45)).isEqualTo(1.0);
        assertThat(scorer.decay(1.0, now.plusDays(2), now, 45)).isEqualTo(1.0);
    }

    @Test
    void repeatedPositiveEvidenceBuildsConfidenceAndAnUpwardTrend() {
        var first = scorer.apply(0, 0, 0, 0, null, 1.0, now, 45);
        var second = scorer.apply(first.positiveScore(), first.negativeScore(), first.preferenceScore(),
                first.interactionCount(), now, 1.0, now.plusHours(1), 45);

        assertThat(first.trend()).isEqualTo("UP");
        assertThat(second.preferenceScore()).isGreaterThan(first.preferenceScore());
        assertThat(second.confidence()).isGreaterThan(first.confidence());
    }

    @Test
    void negativeSignalsLowerTheScoreButOneSkipDoesNotBecomeCertainty() {
        var result = scorer.apply(1.0, 0, 0.35, 2, now, -0.25, now.plusHours(1), 45);

        assertThat(result.preferenceScore()).isLessThan(0.35);
        assertThat(result.confidence()).isLessThan(0.70);
    }
}
