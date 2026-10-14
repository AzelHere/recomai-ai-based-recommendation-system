package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record Category(
        long categoryId,
        String name,
        String description,
        String nicheCode,
        boolean active,
        LocalDateTime createdAt) {
    public Category(long categoryId, String name, String description, boolean active, LocalDateTime createdAt) {
        this(categoryId, name, description, null, active, createdAt);
    }
}
