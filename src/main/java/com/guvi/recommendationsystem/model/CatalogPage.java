package com.guvi.recommendationsystem.model;

import java.util.List;

public record CatalogPage<T>(List<T> items, int page, int pageSize, long totalItems, long totalPages) {
}
