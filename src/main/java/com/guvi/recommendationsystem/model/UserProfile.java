package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record UserProfile(
        long userId,
        String username,
        String email,
        String displayName,
        String role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
