package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.CategoryDao;
import com.guvi.recommendationsystem.dao.InterestDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.ItemInterestDao;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.CatalogCsvPreview;
import com.guvi.recommendationsystem.model.Category;
import com.guvi.recommendationsystem.model.Interest;
import com.guvi.recommendationsystem.model.ItemDifficulty;
import com.guvi.recommendationsystem.model.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogCsvServiceTest {
    private CategoryDao categoryDao;
    private InterestDao interestDao;
    private ItemDao itemDao;
    private ItemInterestDao itemInterestDao;
    private ItemService itemService;
    private CatalogCsvService service;

    @BeforeEach
    void setUp() {
        categoryDao = mock(CategoryDao.class);
        interestDao = mock(InterestDao.class);
        itemDao = mock(ItemDao.class);
        itemInterestDao = mock(ItemInterestDao.class);
        itemService = mock(ItemService.class);
        when(categoryDao.findActive()).thenReturn(List.of(new Category(1, "Technology", "", "TECHNOLOGY", true, LocalDateTime.now())));
        when(interestDao.findActive()).thenReturn(List.of(
                new Interest(1, "Programming", true, LocalDateTime.now(), LocalDateTime.now()),
                new Interest(2, "Web Development", true, LocalDateTime.now(), LocalDateTime.now()),
                new Interest(3, "Databases", true, LocalDateTime.now(), LocalDateTime.now())));
        service = new CatalogCsvService(categoryDao, interestDao, itemDao, itemInterestDao, itemService);
    }

    @Test
    void previewAcceptsAValidRecordAndChecksItsTags() {
        CatalogCsvPreview preview = service.preview(csv("A guide. A second sentence."));

        assertThat(preview.canImport()).isTrue();
        assertThat(preview.validRows()).isEqualTo(1);
        assertThat(preview.invalidRows()).isZero();
    }

    @Test
    void previewReportsUnsupportedTagsAndInsufficientDescription() {
        CatalogCsvPreview preview = service.preview(csv("Short." ).replace("Programming|Web Development|Databases", "Unknown|Web Development|Databases"));

        assertThat(preview.canImport()).isFalse();
        assertThat(preview.rows().getFirst().errors()).anyMatch(message -> message.contains("at least two sentences"));
        assertThat(preview.rows().getFirst().errors()).anyMatch(message -> message.contains("Unknown topic tag"));
    }

    @Test
    void importSkipsExistingUrlsAndAssociatesTagsForNewItems() {
        var item = new CatalogItem(7, 1, "A guide", "A guide. Another sentence.", ItemType.GUIDE,
                "https://example.org/guide", "Example", List.of(), ItemDifficulty.BEGINNER, 30,
                new BigDecimal("0.75"), null, true, LocalDateTime.now(), LocalDateTime.now());
        when(itemDao.existsByExternalUrl("https://example.org/guide")).thenReturn(false);
        when(itemService.create(any())).thenReturn(item);

        var result = service.commit(csv("A guide. Another sentence."));

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedExisting()).isZero();
        verify(itemInterestDao).save(7, 1, BigDecimal.ONE);
        verify(itemInterestDao).save(7, 2, BigDecimal.ONE);
        verify(itemInterestDao).save(7, 3, BigDecimal.ONE);
    }

    @Test
    void commitRejectsAnyInvalidRowBeforeWriting() {
        assertThatThrownBy(() -> service.commit(csv("Only one sentence.")))
                .hasMessageContaining("Fix every invalid CSV row");
        verify(itemService, never()).create(any());
    }

    @Test
    void malformedCsvHeadersAreRejected() {
        assertThatThrownBy(() -> service.preview("title,url\nExample,https://example.org"))
                .hasMessageContaining("CSV headers must be");
    }

    private String csv(String description) {
        return "category,title,description,type,url,source,difficulty,estimatedMinutes,tags\n"
                + "Technology,A guide,\"" + description + "\",GUIDE,https://example.org/guide,Example,BEGINNER,30,"
                + "Programming|Web Development|Databases";
    }
}
