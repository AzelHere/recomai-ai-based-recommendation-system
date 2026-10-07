package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogSort;
import com.guvi.recommendationsystem.model.ItemType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ItemDao {
    Optional<CatalogItem> findById(long itemId);

    List<CatalogItem> findAll();

    List<CatalogItem> findActive();

    List<CatalogItem> findActiveByCategory(long categoryId);

    List<CatalogItem> searchActive(String query, Long categoryId, ItemType type,
                                   CatalogSort sort, int limit, int offset);

    long countActive(String query, Long categoryId, ItemType type);

    CatalogItem insert(long categoryId, String title, String description, ItemType type,
                       String externalUrl, BigDecimal price);

    boolean update(long itemId, long categoryId, String title, String description, ItemType type,
                   String externalUrl, BigDecimal price);

    boolean setActive(long itemId, boolean active);
}
