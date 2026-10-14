package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record Interest(
        long interestId,
        String name,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
