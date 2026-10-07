package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record Interaction(
        long interactionId,
        long userId,
        long itemId,
        InteractionType type,
        LocalDateTime occurredAt) {
}
