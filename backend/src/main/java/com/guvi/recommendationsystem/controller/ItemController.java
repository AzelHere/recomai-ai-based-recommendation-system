package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.model.ActiveStatusRequest;
import com.guvi.recommendationsystem.model.BulkActiveStatusRequest;
import com.guvi.recommendationsystem.model.BulkActiveStatusResult;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogCsvRequest;
import com.guvi.recommendationsystem.model.CatalogCsvPreview;
import com.guvi.recommendationsystem.model.CatalogCsvImportResult;
import com.guvi.recommendationsystem.model.CatalogPage;
import com.guvi.recommendationsystem.model.ItemRequest;
import com.guvi.recommendationsystem.model.ItemType;
import com.guvi.recommendationsystem.service.ItemService;
import com.guvi.recommendationsystem.service.CatalogCsvService;
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
    private final CatalogCsvService catalogCsvService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.guvi.recommendationsystem.service.AdminWriteService writes;
    @org.springframework.beans.factory.annotation.Autowired
    private com.guvi.recommendationsystem.service.AdminCatalogService adminCatalog;

    @GetMapping("/admin/items/search")
    public CatalogPage<CatalogItem> adminSearch(@RequestParam(defaultValue="") String query,
            @RequestParam(required=false) Long categoryId,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="title") String sort,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) ItemType type,HttpServletRequest request){
        sessionAccess.requireAdmin(request);
        return adminCatalog.search(query,categoryId,status,sort,page,pageSize,type);
    }

    @GetMapping("/admin/items/composition")
    public List<java.util.Map<String,Object>> adminComposition(HttpServletRequest request){
        sessionAccess.requireAdmin(request);return adminCatalog.composition();
    }

    /** Reloads one editor record, including paused resources, without downloading the legacy full catalog. */
    @GetMapping("/admin/items/{itemId}")
    public CatalogItem adminItem(@PathVariable long itemId, HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return itemService.get(itemId);
    }

    public ItemController(ItemService itemService, SessionAccess sessionAccess, CatalogCsvService catalogCsvService) {
        this.itemService = itemService;
        this.sessionAccess = sessionAccess;
        this.catalogCsvService = catalogCsvService;
    }

    @GetMapping("/items")
    public List<CatalogItem> getActiveItems(@RequestParam(required = false) Long categoryId) {
        return categoryId == null
                ? itemService.getActive()
                : itemService.getActiveByCategory(categoryId);
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
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Expected-Revision",required=false) Long revision,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return writes.item(itemId,itemRequest,revision);
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

    @PatchMapping("/admin/items/active")
    public BulkActiveStatusResult setItemsActive(
            @RequestBody BulkActiveStatusRequest requestBody, HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        if (requestBody == null || requestBody.active() == null) {
            throw new InvalidUserInputException("Active status is required.");
        }
        return new BulkActiveStatusResult(itemService.setActive(requestBody.itemIds(), requestBody.active()));
    }

    @PostMapping("/admin/items/import/preview")
    public CatalogCsvPreview previewImport(@RequestBody CatalogCsvRequest body, HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return catalogCsvService.preview(body == null ? null : body.csv());
    }

    @PostMapping("/admin/items/import/commit")
    public CatalogCsvImportResult commitImport(@RequestBody CatalogCsvRequest body, HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return catalogCsvService.commit(body == null ? null : body.csv());
    }
}
