package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record Feedback(
        long feedbackId,
        long userId,
        long itemId,
        int rating,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
