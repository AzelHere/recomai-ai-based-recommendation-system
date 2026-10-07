package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.ActiveStatusRequest;
import com.guvi.recommendationsystem.model.Category;
import com.guvi.recommendationsystem.model.CategoryRequest;
import com.guvi.recommendationsystem.service.CategoryService;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CategoryController {
    private final CategoryService categoryService;
    private final SessionAccess sessionAccess;

    public CategoryController(CategoryService categoryService, SessionAccess sessionAccess) {
        this.categoryService = categoryService;
        this.sessionAccess = sessionAccess;
    }

    @GetMapping("/categories")
    public List<Category> getActiveCategories() {
        return categoryService.getActive();
    }

    @GetMapping("/admin/categories")
    public List<Category> getAllCategories(HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return categoryService.getAll();
    }

    @PostMapping("/admin/categories")
    public ResponseEntity<Category> createCategory(
            @RequestBody CategoryRequest categoryRequest,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        Category category = categoryService.create(categoryRequest);
        return ResponseEntity.created(URI.create("/api/admin/categories/" + category.categoryId()))
                .body(category);
    }

    @PutMapping("/admin/categories/{categoryId}")
    public Category updateCategory(
            @PathVariable long categoryId,
            @RequestBody CategoryRequest categoryRequest,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return categoryService.update(categoryId, categoryRequest);
    }

    @PatchMapping("/admin/categories/{categoryId}/active")
    public Category setCategoryActive(
            @PathVariable long categoryId,
            @RequestBody ActiveStatusRequest statusRequest,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        if (statusRequest == null || statusRequest.active() == null) {
            throw new InvalidUserInputException("Active status is required.");
        }
        return statusRequest.active()
                ? categoryService.activate(categoryId)
                : categoryService.deactivate(categoryId);
    }
}
