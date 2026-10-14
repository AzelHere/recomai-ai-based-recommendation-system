package com.guvi.recommendationsystem.model;

import java.util.List;

public record BulkActiveStatusRequest(List<Long> itemIds, Boolean active) {
}
