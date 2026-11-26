package com.guvi.recommendationsystem.model;

import java.util.List;

public record CatalogPage<T>(List<T> items, int page, int pageSize, long totalItems, long totalPages) {
    public CatalogPage {
        items = items == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
    }

}
