package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.model.ItemType;
import com.guvi.recommendationsystem.service.RecommendationService;
import com.guvi.recommendationsystem.web.ApiExceptionHandler;
import com.guvi.recommendationsystem.web.SessionAccess;
import com.guvi.recommendationsystem.dao.UserDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalInt;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
@Import({SessionAccess.class, ApiExceptionHandler.class})
class RecommendationControllerWebTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RecommendationService recommendationService;
    @MockitoBean private UserDao userDao;

    @Test
    void catalogDiscoveryRequiresSessionAndReturnsEligibleCatalogItems() throws Exception {
        when(userDao.findAuthVersion(9L)).thenReturn(OptionalInt.of(1));
        CatalogItem item = new CatalogItem(12, 4, "General resource", null, ItemType.ARTICLE,
                "https://example.com", null, List.of(), com.guvi.recommendationsystem.model.ItemDifficulty.ALL_LEVELS,
                null, java.math.BigDecimal.valueOf(0.8), null, true, LocalDateTime.now(), LocalDateTime.now(), true);
        when(recommendationService.getCatalogDiscoveryForUser(9L, 3)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/users/me/recommendations/discovery?limit=3"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/recommendations/discovery?limit=3")
                        .sessionAttr("authenticatedUserId", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("General resource"))
                .andExpect(jsonPath("$[0].editorPick").value(true));
    }

    @Test
    void impressionTrackingRequiresSessionAndUsesTheAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/users/me/recommendations/31/impression"))
                .andExpect(status().isUnauthorized());

        when(userDao.findAuthVersion(9L)).thenReturn(OptionalInt.of(1));
        mockMvc.perform(post("/api/users/me/recommendations/31/impression")
                        .sessionAttr("authenticatedUserId", 9L)
                        .sessionAttr("authenticatedUserVersion", 1))
                .andExpect(status().isNoContent());
        verify(recommendationService).recordImpression(9L, 31L);
    }

    @Test
    void clickTrackingRequiresSessionAndUsesTheAuthenticatedUser() throws Exception {
        when(userDao.findAuthVersion(9L)).thenReturn(OptionalInt.of(1));
        mockMvc.perform(post("/api/users/me/recommendations/31/click")
                        .sessionAttr("authenticatedUserId", 9L)
                        .sessionAttr("authenticatedUserVersion", 1))
                .andExpect(status().isNoContent());
        verify(recommendationService).recordClick(9L, 31L);
    }
}
