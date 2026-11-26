package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.model.AdminSystemSettings;
import com.guvi.recommendationsystem.service.AdminSettingsService;
import com.guvi.recommendationsystem.web.ApiExceptionHandler;
import com.guvi.recommendationsystem.web.SessionAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.OptionalInt;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSettingsController.class)
@Import({SessionAccess.class, ApiExceptionHandler.class})
class AdminSettingsControllerWebTest {
    @MockitoBean private com.guvi.recommendationsystem.service.AdminWriteService writes;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminSettingsService service;
    @MockitoBean private UserDao userDao;

    @BeforeEach void authenticatedSessionVersion() { when(userDao.findAuthVersion(21L)).thenReturn(OptionalInt.of(1)); }

    @Test void settingsRequireAnAdministrator() throws Exception {
        mockMvc.perform(get("/api/admin/settings").sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "USER"))
                .andExpect(status().isForbidden());
        verify(service, never()).getSettings();
    }

    @Test void administratorCanReadCurrentSettings() throws Exception {
        when(service.getSettings()).thenReturn(new AdminSystemSettings(new BigDecimal("0.7000"),
                new BigDecimal("0.2000"), new BigDecimal("0.1000"), LocalDateTime.now()));
        mockMvc.perform(get("/api/admin/settings").sessionAttr("authenticatedUserId", 21L)
                        .sessionAttr("authenticatedUserRole", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.familiarShare").value(0.7));
    }

    @Test void updateRequiresAnAdministrator() throws Exception {
        mockMvc.perform(put("/api/admin/settings").contentType("application/json")
                        .content("{\"familiarShare\":0.5,\"relatedShare\":0.3,\"discoveryShare\":0.2}")
                        .sessionAttr("authenticatedUserId", 21L).sessionAttr("authenticatedUserRole", "USER"))
                .andExpect(status().isForbidden());
        verify(service, never()).update(any(), anyLong());
    }
}
