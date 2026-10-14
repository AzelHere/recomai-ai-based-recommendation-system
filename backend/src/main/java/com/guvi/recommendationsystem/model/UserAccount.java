package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;

public record UserAccount(
        long userId,
        String username,
        String email,
        String passwordHash,
        String displayName,
        String role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
