package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryDao {
    Optional<Category> findById(long categoryId);

    List<Category> findAll();

    List<Category> findActive();

    boolean existsById(long categoryId);

    boolean hasActiveItems(long categoryId);

    Category insert(String name, String description);

    boolean update(long categoryId, String name, String description);

    boolean setActive(long categoryId, boolean active);
}
