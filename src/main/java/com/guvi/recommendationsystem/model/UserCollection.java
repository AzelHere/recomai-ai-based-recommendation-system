package com.guvi.recommendationsystem.model;

import java.time.LocalDateTime;
import java.util.List;

public record UserCollection(long collectionId, String name, LocalDateTime updatedAt, List<Long> itemIds) {
    public UserCollection {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
    }
}
