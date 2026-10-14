package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

public record ItemRequest(
        long categoryId,
        String title,
        String description,
        ItemType type,
        String externalUrl,
        BigDecimal price,
        String sourceName,
        ItemDifficulty difficulty,
        Integer estimatedMinutes,
        BigDecimal qualityScore,
        Boolean editorPick) {
    public ItemRequest(long categoryId, String title, String description, ItemType type, String externalUrl,
                       BigDecimal price, String sourceName, ItemDifficulty difficulty,
                       Integer estimatedMinutes, BigDecimal qualityScore) {
        this(categoryId, title, description, type, externalUrl, price, sourceName, difficulty,
                estimatedMinutes, qualityScore, false);
    }

    public ItemRequest(long categoryId, String title, String description, ItemType type,
                       String externalUrl, BigDecimal price) {
        this(categoryId, title, description, type, externalUrl, price, null,
                ItemDifficulty.ALL_LEVELS, null, BigDecimal.valueOf(0.5), false);
    }
}
