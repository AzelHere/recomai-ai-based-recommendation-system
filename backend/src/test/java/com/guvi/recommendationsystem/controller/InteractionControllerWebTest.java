package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.InteractionType;
import com.guvi.recommendationsystem.service.InteractionService;
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
import java.util.OptionalInt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InteractionController.class)
@Import({SessionAccess.class, ApiExceptionHandler.class})
class InteractionControllerWebTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean private UserDao userDao;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private InteractionService interactionService;

    @org.junit.jupiter.api.BeforeEach
    void userAuthVersionExistsForAuthenticatedTestSessions() {
        when(userDao.findAuthVersion(9L)).thenReturn(OptionalInt.of(1));
    }

    @Test
    void searchEventsRequireAnAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/users/me/interactions/search")
                        .contentType("application/json")
                        .content("{\"query\":\"Cloud Computing\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(interactionService);
    }

    @Test
    void searchEventReturnsAUserScopedSearchDto() throws Exception {
        when(interactionService.trackSearch(eq(9L), any())).thenReturn(
                new Interaction(14L, 9L, null, InteractionType.SEARCH,
                        LocalDateTime.of(2026, 10, 7, 12, 0), "Cloud Computing"));

        mockMvc.perform(post("/api/users/me/interactions/search")
                        .sessionAttr("authenticatedUserId", 9L)
                        .contentType("application/json")
                        .content("{\"query\":\"Cloud Computing\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SEARCH"))
                .andExpect(jsonPath("$.itemId").doesNotExist())
                .andExpect(jsonPath("$.searchQuery").value("Cloud Computing"));
    }

    @Test
    void currentProgressEndpointRequiresSessionAndReturnsLatestStateEvents() throws Exception {
        when(interactionService.getCurrentProgressForUser(9L)).thenReturn(java.util.List.of(
                new Interaction(15L, 9L, 22L, InteractionType.READ_PROGRESS,
                        LocalDateTime.of(2026, 10, 10, 12, 0))));

        mockMvc.perform(get("/api/users/me/interactions/progress"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/interactions/progress")
                        .sessionAttr("authenticatedUserId", 9L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("READ_PROGRESS"))
                .andExpect(jsonPath("$[0].itemId").value(22));
    }
}
