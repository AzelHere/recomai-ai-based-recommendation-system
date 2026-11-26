package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CatalogItem(
        long itemId,
        long categoryId,
        String title,
        String description,
        ItemType type,
        String externalUrl,
        String sourceName,
        List<String> tags,
        ItemDifficulty difficulty,
        Integer estimatedMinutes,
        BigDecimal qualityScore,
        BigDecimal price,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean editorPick,long revision) {
    public CatalogItem(long itemId,long categoryId,String title,String description,ItemType type,String externalUrl,String sourceName,List<String> tags,ItemDifficulty difficulty,Integer estimatedMinutes,BigDecimal qualityScore,BigDecimal price,boolean active,LocalDateTime createdAt,LocalDateTime updatedAt,boolean editorPick){this(itemId,categoryId,title,description,type,externalUrl,sourceName,tags,difficulty,estimatedMinutes,qualityScore,price,active,createdAt,updatedAt,editorPick,0);}
    public CatalogItem(long itemId, long categoryId, String title, String description, ItemType type,
                       String externalUrl, String sourceName, List<String> tags, ItemDifficulty difficulty,
                       Integer estimatedMinutes, BigDecimal qualityScore, BigDecimal price, boolean active,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(itemId, categoryId, title, description, type, externalUrl, sourceName, tags, difficulty,
                estimatedMinutes, qualityScore, price, active, createdAt, updatedAt, false);
    }

    public CatalogItem(long itemId, long categoryId, String title, String description, ItemType type,
                       String externalUrl, BigDecimal price, boolean active, LocalDateTime createdAt,
                       LocalDateTime updatedAt) {
        this(itemId, categoryId, title, description, type, externalUrl, null, List.of(), ItemDifficulty.ALL_LEVELS,
                null, BigDecimal.valueOf(0.5), price, active, createdAt, updatedAt, false);
    }
}
