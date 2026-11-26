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
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;
    private void catalogChanged(){if(events!=null)events.publishEvent(new com.guvi.recommendationsystem.catalog.CatalogImported(0));}


    public CategoryService(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    @org.springframework.transaction.annotation.Transactional
    public Category create(CategoryRequest request) {
        CategoryRequest validated = validate(request);
        try {
            var created=categoryDao.insert(validated.name(), validated.description());catalogChanged();return created;
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

    @org.springframework.transaction.annotation.Transactional
    public Category update(long categoryId, CategoryRequest request) {
        validateId(categoryId);
        CategoryRequest validated = validate(request);
        if (!categoryDao.existsById(categoryId)) {
            throw new CategoryNotFoundException("Category was not found.");
        }
        try {
            categoryDao.update(categoryId, validated.name(), validated.description());catalogChanged();
        } catch (DuplicateKeyException exception) {
            throw new DuplicateCatalogEntryException("A category with that name already exists.");
        }
        return get(categoryId);
    }

    @org.springframework.transaction.annotation.Transactional
    public Category deactivate(long categoryId) {
        validateId(categoryId);
        get(categoryId);
        if (categoryDao.hasActiveItems(categoryId)) {
            throw new CategoryInUseException("Deactivate or move the category's active items first.");
        }
        categoryDao.setActive(categoryId, false);catalogChanged();
        return get(categoryId);
    }

    @org.springframework.transaction.annotation.Transactional
    public Category activate(long categoryId) {
        validateId(categoryId);
        if (!categoryDao.existsById(categoryId)) {
            throw new CategoryNotFoundException("Category was not found.");
        }
        categoryDao.setActive(categoryId, true);catalogChanged();
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
