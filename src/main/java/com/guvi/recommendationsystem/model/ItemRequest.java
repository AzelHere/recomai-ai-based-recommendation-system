package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

public record ItemRequest(
        long categoryId,
        String title,
        String description,
        ItemType type,
        String externalUrl,
        BigDecimal price) {
}
