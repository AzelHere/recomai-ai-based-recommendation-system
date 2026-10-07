package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.LoginRequest;
import com.guvi.recommendationsystem.model.UserProfile;
import com.guvi.recommendationsystem.service.UserAccountService;
import com.guvi.recommendationsystem.web.ApiExceptionHandler;
import com.guvi.recommendationsystem.web.SessionAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SessionAccess.class, ApiExceptionHandler.class})
class AuthControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountService userAccountService;

    @Test
    void loginReturnsProfileAndStartsAnAuthenticatedSession() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        UserProfile profile = new UserProfile(
                19L, "sufiyan", "s@example.com", "Sufiyan", "USER", now, now);
        when(userAccountService.authenticate(any(LoginRequest.class))).thenReturn(Optional.of(profile));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"s@example.com\",\"password\":\"password-9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("sufiyan"))
                .andExpect(request().sessionAttribute("authenticatedUserId", 19L));
    }

    @Test
    void invalidCredentialsReturnAConsistentUnauthorizedError() throws Exception {
        when(userAccountService.authenticate(any(LoginRequest.class))).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"s@example.com\",\"password\":\"incorrect\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }
}
