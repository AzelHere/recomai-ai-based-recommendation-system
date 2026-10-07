package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CatalogItem(
        long itemId,
        long categoryId,
        String title,
        String description,
        ItemType type,
        String externalUrl,
        BigDecimal price,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
