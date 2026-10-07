package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.model.ActiveStatusRequest;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogPage;
import com.guvi.recommendationsystem.model.CatalogSort;
import com.guvi.recommendationsystem.model.ItemRequest;
import com.guvi.recommendationsystem.model.ItemType;
import com.guvi.recommendationsystem.service.ItemService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ItemController {
    private final ItemService itemService;
    private final SessionAccess sessionAccess;

    public ItemController(ItemService itemService, SessionAccess sessionAccess) {
        this.itemService = itemService;
        this.sessionAccess = sessionAccess;
    }

    @GetMapping("/items")
    public List<CatalogItem> getActiveItems(@RequestParam(required = false) Long categoryId) {
        return categoryId == null
                ? itemService.getActive()
                : itemService.getActiveByCategory(categoryId);
    }

    @GetMapping("/items/search")
    public CatalogPage<CatalogItem> searchActiveItems(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ItemType type,
            @RequestParam(defaultValue = "NEWEST") CatalogSort sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return itemService.searchActive(query, categoryId, type, sort, page, pageSize);
    }

    @GetMapping("/items/{itemId}")
    public CatalogItem getActiveItem(@PathVariable long itemId) {
        CatalogItem item = itemService.get(itemId);
        if (!item.active()) {
            throw new ItemNotFoundException("Item was not found.");
        }
        return item;
    }

    @GetMapping("/admin/items")
    public List<CatalogItem> getAllItems(HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return itemService.getAll();
    }

    @PostMapping("/admin/items")
    public ResponseEntity<CatalogItem> createItem(
            @RequestBody ItemRequest itemRequest,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        CatalogItem item = itemService.create(itemRequest);
        return ResponseEntity.created(URI.create("/api/items/" + item.itemId())).body(item);
    }

    @PutMapping("/admin/items/{itemId}")
    public CatalogItem updateItem(
            @PathVariable long itemId,
            @RequestBody ItemRequest itemRequest,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return itemService.update(itemId, itemRequest);
    }

    @PatchMapping("/admin/items/{itemId}/active")
    public CatalogItem setItemActive(
            @PathVariable long itemId,
            @RequestBody ActiveStatusRequest statusRequest,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        if (statusRequest == null || statusRequest.active() == null) {
            throw new InvalidUserInputException("Active status is required.");
        }
        return statusRequest.active()
                ? itemService.activate(itemId)
                : itemService.deactivate(itemId);
    }
}
