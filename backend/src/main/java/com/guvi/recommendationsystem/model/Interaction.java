package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record Interaction(
        long interactionId,
        long userId,
        Long itemId,
        InteractionType type,
        LocalDateTime occurredAt,
        String searchQuery) {

    public Interaction(long interactionId, long userId, long itemId, InteractionType type,
                       LocalDateTime occurredAt) {
        this(interactionId, userId, itemId, type, occurredAt, null);
    }
}
