package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.model.BulkActiveStatusResult;
import com.guvi.recommendationsystem.model.CatalogCsvImportResult;
import com.guvi.recommendationsystem.model.CatalogCsvPreview;
import com.guvi.recommendationsystem.service.CatalogCsvService;
import com.guvi.recommendationsystem.service.ItemService;
import com.guvi.recommendationsystem.web.ApiExceptionHandler;
import com.guvi.recommendationsystem.web.SessionAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.OptionalInt;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemController.class)
@Import({SessionAccess.class, ApiExceptionHandler.class})
class ItemAdminControllerWebTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ItemService itemService;
    @MockitoBean private CatalogCsvService catalogCsvService;
    @MockitoBean private UserDao userDao;

    @BeforeEach
    void authVersionIsCurrent() { when(userDao.findAuthVersion(21L)).thenReturn(OptionalInt.of(1)); }

    @Test
    void csvPreviewRequiresAdminAndDoesNotReachServiceWithoutSession() throws Exception {
        mockMvc.perform(post("/api/admin/items/import/preview")
                        .contentType("application/json").content("{\"csv\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(catalogCsvService);
    }

    @Test
    void userRoleCannotPreviewCsv() throws Exception {
        mockMvc.perform(post("/api/admin/items/import/preview")
                        .sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "USER")
                        .sessionAttr("authenticatedUserVersion", 1)
                        .contentType("application/json").content("{\"csv\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(catalogCsvService);
    }

    @Test
    void administratorCanPreviewAndCommitCsv() throws Exception {
        when(catalogCsvService.preview("sample")).thenReturn(new CatalogCsvPreview(1, 0, true, List.of()));
        when(catalogCsvService.commit("sample")).thenReturn(new CatalogCsvImportResult(1, 0));

        mockMvc.perform(post("/api/admin/items/import/preview")
                        .sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "ADMIN")
                        .sessionAttr("authenticatedUserVersion", 1)
                        .contentType("application/json").content("{\"csv\":\"sample\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canImport").value(true));
        mockMvc.perform(post("/api/admin/items/import/commit")
                        .sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "ADMIN")
                        .sessionAttr("authenticatedUserVersion", 1)
                        .contentType("application/json").content("{\"csv\":\"sample\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));
        verify(catalogCsvService).preview("sample");
        verify(catalogCsvService).commit("sample");
    }

    @Test
    void administratorCanBulkUpdateSelectedItems() throws Exception {
        when(itemService.setActive(List.of(1L, 2L), false)).thenReturn(2);
        mockMvc.perform(patch("/api/admin/items/active")
                        .sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "ADMIN")
                        .sessionAttr("authenticatedUserVersion", 1)
                        .contentType("application/json")
                        .content("{\"itemIds\":[1,2],\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(2));
        verify(itemService).setActive(List.of(1L, 2L), false);
    }
}
