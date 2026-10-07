package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.CategoryDao;
import com.guvi.recommendationsystem.exception.CategoryInUseException;
import com.guvi.recommendationsystem.exception.CategoryNotFoundException;
import com.guvi.recommendationsystem.exception.DuplicateCatalogEntryException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.Category;
import com.guvi.recommendationsystem.model.CategoryRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryDao categoryDao;

    public CategoryService(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    public Category create(CategoryRequest request) {
        CategoryRequest validated = validate(request);
        try {
            return categoryDao.insert(validated.name(), validated.description());
        } catch (DuplicateKeyException exception) {
            throw new DuplicateCatalogEntryException("A category with that name already exists.");
        }
    }

    public Category get(long categoryId) {
        validateId(categoryId);
        return categoryDao.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category was not found."));
    }

    public List<Category> getAll() {
        return categoryDao.findAll();
    }

    public List<Category> getActive() {
        return categoryDao.findActive();
    }

    public Category update(long categoryId, CategoryRequest request) {
        validateId(categoryId);
        CategoryRequest validated = validate(request);
        if (!categoryDao.existsById(categoryId)) {
            throw new CategoryNotFoundException("Category was not found.");
        }
        try {
            categoryDao.update(categoryId, validated.name(), validated.description());
        } catch (DuplicateKeyException exception) {
            throw new DuplicateCatalogEntryException("A category with that name already exists.");
        }
        return get(categoryId);
    }

    public Category deactivate(long categoryId) {
        validateId(categoryId);
        Category category = get(categoryId);
        if (categoryDao.hasActiveItems(categoryId)) {
            throw new CategoryInUseException("Deactivate or move the category's active items first.");
        }
        categoryDao.setActive(categoryId, false);
        return get(categoryId);
    }

    public Category activate(long categoryId) {
        validateId(categoryId);
        if (!categoryDao.existsById(categoryId)) {
            throw new CategoryNotFoundException("Category was not found.");
        }
        categoryDao.setActive(categoryId, true);
        return get(categoryId);
    }

    private CategoryRequest validate(CategoryRequest request) {
        if (request == null || request.name() == null || request.name().trim().isEmpty()) {
            throw new InvalidUserInputException("Category name is required.");
        }
        String name = request.name().trim();
        String description = request.description() == null || request.description().isBlank()
                ? null
                : request.description().trim();
        if (name.length() > 100) {
            throw new InvalidUserInputException("Category name must be 100 characters or fewer.");
        }
        if (description != null && description.length() > 500) {
            throw new InvalidUserInputException("Category description must be 500 characters or fewer.");
        }
        return new CategoryRequest(name, description);
    }

    private void validateId(long categoryId) {
        if (categoryId <= 0) {
            throw new InvalidUserInputException("Category ID must be positive.");
        }
    }
}
