package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.CategoryDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.exception.CategoryInUseException;
import com.guvi.recommendationsystem.exception.CategoryNotFoundException;
import com.guvi.recommendationsystem.exception.DuplicateCatalogEntryException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogSort;
import com.guvi.recommendationsystem.model.Category;
import com.guvi.recommendationsystem.model.CategoryRequest;
import com.guvi.recommendationsystem.model.ItemRequest;
import com.guvi.recommendationsystem.model.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {
    @Mock
    private CategoryDao categoryDao;
    @Mock
    private ItemDao itemDao;

    private CategoryService categoryService;
    private ItemService itemService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryDao);
        itemService = new ItemService(itemDao, categoryDao);
    }

    @Test
    void categoryCreationTrimsFieldsAndMapsDuplicateNames() {
        when(categoryDao.insert("Books", null)).thenReturn(category(2L, true));

        Category created = categoryService.create(new CategoryRequest(" Books ", " "));

        assertThat(created.categoryId()).isEqualTo(2L);
        verify(categoryDao).insert("Books", null);
        when(categoryDao.insert("Books", null)).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatExceptionOfType(DuplicateCatalogEntryException.class)
                .isThrownBy(() -> categoryService.create(new CategoryRequest("Books", null)));
    }

    @Test
    void categoryCannotBeDeactivatedWhileItContainsActiveItems() {
        when(categoryDao.findById(2L)).thenReturn(Optional.of(category(2L, true)));
        when(categoryDao.hasActiveItems(2L)).thenReturn(true);

        assertThatExceptionOfType(CategoryInUseException.class)
                .isThrownBy(() -> categoryService.deactivate(2L));
    }

    @Test
    void itemCreationTrimsTextAndStoresOptionalPrice() {
        CatalogItem item = new CatalogItem(
                5L, 2L, "Notebook", null, ItemType.PRODUCT, null,
                new BigDecimal("8.50"), true, LocalDateTime.now(), LocalDateTime.now());
        when(categoryDao.findById(2L)).thenReturn(Optional.of(category(2L, true)));
        when(itemDao.insert(eq(2L), eq("Notebook"), any(), eq(ItemType.PRODUCT), any(), eq(new BigDecimal("8.50"))))
                .thenReturn(item);

        CatalogItem created = itemService.create(new ItemRequest(
                2L, " Notebook ", "  ", ItemType.PRODUCT, null, new BigDecimal("8.50")));

        assertThat(created.itemId()).isEqualTo(5L);
        verify(itemDao).insert(eq(2L), eq("Notebook"), any(), eq(ItemType.PRODUCT), any(), eq(new BigDecimal("8.50")));
    }

    @Test
    void itemCreationRequiresAnActiveCategoryAndHttpUrlWhenSupplied() {
        when(categoryDao.findById(2L))
                .thenReturn(Optional.of(category(2L, false)))
                .thenReturn(Optional.of(category(2L, true)));
        assertThatExceptionOfType(CategoryNotFoundException.class)
                .isThrownBy(() -> itemService.create(new ItemRequest(
                        2L, "Course", null, ItemType.CONTENT, null, null)));

        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> itemService.create(new ItemRequest(
                        2L, "Course", null, ItemType.CONTENT, "javascript:alert(1)", null)));
        verifyNoInteractions(itemDao);
    }

    @Test
    void catalogSearchReturnsPageMetadataAndUsesTheRequestedFilters() {
        CatalogItem result = new CatalogItem(5L, 2L, "Java", null, ItemType.COURSE,
                null, null, true, LocalDateTime.now(), LocalDateTime.now());
        when(itemDao.countActive("Java", 2L, ItemType.COURSE)).thenReturn(1L);
        when(itemDao.searchActive("Java", 2L, ItemType.COURSE, CatalogSort.MOST_POPULAR, 10, 0))
                .thenReturn(java.util.List.of(result));

        var page = itemService.searchActive(" Java ", 2L, ItemType.COURSE, CatalogSort.MOST_POPULAR, 1, 10);

        assertThat(page.items()).containsExactly(result);
        assertThat(page.totalItems()).isEqualTo(1L);
        assertThat(page.totalPages()).isEqualTo(1L);
        verify(itemDao).searchActive("Java", 2L, ItemType.COURSE, CatalogSort.MOST_POPULAR, 10, 0);
    }

    @Test
    void catalogSearchRejectsLongQueriesBeforeDatabaseAccess() {
        assertThatExceptionOfType(InvalidUserInputException.class)
                .isThrownBy(() -> itemService.searchActive("x".repeat(121), null, null,
                        CatalogSort.NEWEST, 1, 20));
        verifyNoInteractions(itemDao);
    }

    private Category category(long id, boolean active) {
        return new Category(id, "Books", null, active, LocalDateTime.now());
    }
}
