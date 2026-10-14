package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.CategoryDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.exception.CategoryNotFoundException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogPage;
import com.guvi.recommendationsystem.model.CatalogSort;
import com.guvi.recommendationsystem.model.ItemRequest;
import com.guvi.recommendationsystem.model.ItemType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashSet;

@Service
public class ItemService {
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private final ItemDao itemDao;
    private final CategoryDao categoryDao;

    public ItemService(ItemDao itemDao, CategoryDao categoryDao) {
        this.itemDao = itemDao;
        this.categoryDao = categoryDao;
    }

    public CatalogItem create(ItemRequest request) {
        ItemRequest validated = validate(request);
        ensureActiveCategory(validated.categoryId());
        return itemDao.insert(
                validated.categoryId(),
                validated.title(),
                validated.description(),
                validated.type(),
                validated.externalUrl(),
                validated.price(), validated.sourceName(), validated.difficulty(),
                validated.estimatedMinutes(), validated.qualityScore(), Boolean.TRUE.equals(validated.editorPick()));
    }

    public CatalogItem get(long itemId) {
        validateId(itemId);
        return itemDao.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Item was not found."));
    }

    public List<CatalogItem> getAll() {
        return itemDao.findAll();
    }

    public List<CatalogItem> getActive() {
        return itemDao.findActive();
    }

    public List<CatalogItem> getActiveByCategory(long categoryId) {
        validateCategoryId(categoryId);
        return itemDao.findActiveByCategory(categoryId);
    }

    public CatalogPage<CatalogItem> searchActive(
            String query, Long categoryId, ItemType type, CatalogSort sort, int page, int pageSize) {
        if (query != null && query.trim().length() > 120) {
            throw new InvalidUserInputException("Search text must be 120 characters or fewer.");
        }
        if (categoryId != null) {
            validateCategoryId(categoryId);
        }
        if (sort == null) {
            throw new InvalidUserInputException("A catalog sort order is required.");
        }
        if (page < 1 || page > 1_000_000) {
            throw new InvalidUserInputException("Page must be between 1 and 1000000.");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new InvalidUserInputException("Page size must be between 1 and 100.");
        }
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        long total = itemDao.countActive(normalizedQuery, categoryId, type);
        int offset = Math.multiplyExact(page - 1, pageSize);
        List<CatalogItem> items = itemDao.searchActive(
                normalizedQuery, categoryId, type, sort, pageSize, offset);
        long totalPages = total / pageSize + (total % pageSize == 0 ? 0 : 1);
        return new CatalogPage<>(items, page, pageSize, total, totalPages);
    }

    public CatalogItem update(long itemId, ItemRequest request) {
        validateId(itemId);
        ItemRequest validated = validate(request);
        if (itemDao.findById(itemId).isEmpty()) {
            throw new ItemNotFoundException("Item was not found.");
        }
        ensureActiveCategory(validated.categoryId());
        itemDao.update(
                itemId,
                validated.categoryId(),
                validated.title(),
                validated.description(),
                validated.type(),
                validated.externalUrl(),
                validated.price(), validated.sourceName(), validated.difficulty(),
                validated.estimatedMinutes(), validated.qualityScore(), Boolean.TRUE.equals(validated.editorPick()));
        return get(itemId);
    }

    public CatalogItem deactivate(long itemId) {
        return setActive(itemId, false);
    }

    public CatalogItem activate(long itemId) {
        CatalogItem item = get(itemId);
        ensureActiveCategory(item.categoryId());
        return setActive(itemId, true);
    }

    public int setActive(Collection<Long> itemIds, boolean active) {
        if (itemIds == null || itemIds.isEmpty() || itemIds.size() > 500
                || itemIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidUserInputException("Provide between 1 and 500 valid item IDs.");
        }
        List<Long> distinctIds = new LinkedHashSet<>(itemIds).stream().toList();
        for (Long id : distinctIds) {
            CatalogItem item = get(id);
            if (active) ensureActiveCategory(item.categoryId());
        }
        return itemDao.setActive(distinctIds, active);
    }

    private CatalogItem setActive(long itemId, boolean active) {
        validateId(itemId);
        CatalogItem existing = get(itemId);
        if (existing.active() == active) {
            return existing;
        }
        if (!itemDao.setActive(itemId, active)) {
            throw new ItemNotFoundException("Item was not found.");
        }
        return get(itemId);
    }

    private ItemRequest validate(ItemRequest request) {
        if (request == null) {
            throw new InvalidUserInputException("Item details are required.");
        }
        validateCategoryId(request.categoryId());
        if (request.title() == null || request.title().trim().isEmpty()) {
            throw new InvalidUserInputException("Item title is required.");
        }
        String title = request.title().trim();
        if (title.length() > 150) {
            throw new InvalidUserInputException("Item title must be 150 characters or fewer.");
        }
        String description = request.description() == null || request.description().isBlank()
                ? null
                : request.description().trim();
        if (description != null && description.length() > 60_000) {
            throw new InvalidUserInputException("Item description is too long.");
        }
        if (request.type() == null) {
            throw new InvalidUserInputException("An item type is required.");
        }
        String externalUrl = validateUrl(request.externalUrl());
        BigDecimal price = request.price();
        if (price != null && (price.signum() < 0 || price.compareTo(MAX_PRICE) > 0
                || price.scale() > 2)) {
            throw new InvalidUserInputException("Price must be between 0 and 99999999.99 with at most two decimal places.");
        }
        String sourceName = request.sourceName() == null || request.sourceName().isBlank()
                ? null : request.sourceName().trim();
        if (sourceName != null && sourceName.length() > 160) {
            throw new InvalidUserInputException("Source name must be 160 characters or fewer.");
        }
        var difficulty = request.difficulty() == null
                ? com.guvi.recommendationsystem.model.ItemDifficulty.ALL_LEVELS : request.difficulty();
        Integer estimatedMinutes = request.estimatedMinutes();
        if (estimatedMinutes != null && (estimatedMinutes < 1 || estimatedMinutes > 1440)) {
            throw new InvalidUserInputException("Estimated time must be between 1 and 1440 minutes.");
        }
        BigDecimal quality = request.qualityScore() == null ? new BigDecimal("0.5000") : request.qualityScore();
        if (quality.compareTo(BigDecimal.ZERO) < 0 || quality.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidUserInputException("Quality score must be between 0 and 1.");
        }
        return new ItemRequest(request.categoryId(), title, description, request.type(), externalUrl, price,
                sourceName, difficulty, estimatedMinutes, quality, Boolean.TRUE.equals(request.editorPick()));
    }

    private String validateUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 2048) {
            throw new InvalidUserInputException("External URL must be 2048 characters or fewer.");
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new InvalidUserInputException("External URL must be an absolute HTTP or HTTPS URL.");
            }
        } catch (URISyntaxException exception) {
            throw new InvalidUserInputException("External URL is not valid.");
        }
        return normalized;
    }

    private void ensureActiveCategory(long categoryId) {
        if (categoryDao.findById(categoryId).filter(category -> category.active()).isEmpty()) {
            throw new CategoryNotFoundException("An active category with that ID was not found.");
        }
    }

    private void validateCategoryId(long categoryId) {
        if (categoryId <= 0) {
            throw new InvalidUserInputException("Category ID must be positive.");
        }
    }

    private void validateId(long itemId) {
        if (itemId <= 0) {
            throw new InvalidUserInputException("Item ID must be positive.");
        }
    }
}
