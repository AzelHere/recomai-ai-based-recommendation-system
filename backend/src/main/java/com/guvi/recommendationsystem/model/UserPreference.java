package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserPreference(
        long categoryId,
        BigDecimal weight,
        LocalDateTime updatedAt) {
}
