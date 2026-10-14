package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.Feedback;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.service.FeedbackService;
import com.guvi.recommendationsystem.web.ApiExceptionHandler;
import com.guvi.recommendationsystem.web.SessionAccess;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedbackController.class)
@Import({SessionAccess.class, ApiExceptionHandler.class})
class FeedbackControllerWebTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private FeedbackService feedbackService;
    @MockitoBean private UserDao userDao;

    @org.junit.jupiter.api.BeforeEach
    void userAuthVersionExistsForAuthenticatedTestSessions() {
        when(userDao.findAuthVersion(9L)).thenReturn(OptionalInt.of(1));
    }

    @Test
    void feedbackRequiresAnAuthenticatedOwner() throws Exception {
        mockMvc.perform(post("/api/users/me/feedback")
                        .contentType("application/json")
                        .content("{\"itemId\":8,\"rating\":5}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(feedbackService);
    }

    @Test
    void feedbackPostUsesTheDocumentedRouteAndReturnsCreated() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(feedbackService.create(eq(9L), any())).thenReturn(
                new Feedback(13L, 9L, 8L, 5, "Useful", now, now));

        mockMvc.perform(post("/api/users/me/feedback")
                        .sessionAttr("authenticatedUserId", 9L)
                        .contentType("application/json")
                        .content("{\"itemId\":8,\"rating\":5,\"comment\":\"Useful\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemId").value(8))
                .andExpect(jsonPath("$.rating").value(5));
    }
}
