package com.guvi.recommendationsystem.model;

import java.util.List;

public record BulkActiveStatusRequest(List<Long> itemIds, Boolean active) {
    public BulkActiveStatusRequest {
        itemIds = itemIds == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(itemIds));
    }

}
