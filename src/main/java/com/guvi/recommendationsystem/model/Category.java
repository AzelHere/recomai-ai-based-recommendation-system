package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record Category(
        long categoryId,
        String name,
        String description,
        boolean active,
        LocalDateTime createdAt) {
}
