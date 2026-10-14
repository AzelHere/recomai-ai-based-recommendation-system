package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.LoginRequest;
import com.guvi.recommendationsystem.model.RegisterUserRequest;
import com.guvi.recommendationsystem.model.UserProfile;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.service.PasswordResetService;
import com.guvi.recommendationsystem.exception.DuplicateUserException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
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
import static org.mockito.Mockito.verify;

@WebMvcTest(AuthController.class)
@Import({SessionAccess.class, ApiExceptionHandler.class})
class AuthControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountService userAccountService;
    @MockitoBean private UserDao userDao;
    @MockitoBean private PasswordResetService passwordResetService;

    @Test
    void registrationReturnsCreatedProfileAndStartsAnAuthenticatedSession() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        UserProfile profile = new UserProfile(
                23L, "new-user", "new@example.com", "New User", "USER", now, now);
        when(userAccountService.register(any(RegisterUserRequest.class))).thenReturn(profile);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"email\":\"new@example.com\","
                                + "\"password\":\"password-9\",\"displayName\":\"New User\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("new-user"))
                .andExpect(request().sessionAttribute("authenticatedUserId", 23L));
    }

    @Test
    void duplicateRegistrationReturnsConflictInsteadOfCreatingASession() throws Exception {
        when(userAccountService.register(any(RegisterUserRequest.class)))
                .thenThrow(new DuplicateUserException("That username or email address is already registered."));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"existing-user\",\"email\":\"existing@example.com\","
                                + "\"password\":\"password-9\",\"displayName\":\"Existing User\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("That username or email address is already registered."))
                .andExpect(request().sessionAttributeDoesNotExist("authenticatedUserId"));
    }

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

    @Test
    void resetRequestUsesAnIdenticalAcceptedResponseForAnyWellFormedEmail() throws Exception {
        String body = "{\"message\":\"If an account exists for this email address, password reset instructions will be sent.\"}";
        when(passwordResetService.normalizeAndValidateEmail(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"known@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json(body));
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json(body));
        verify(passwordResetService).requestReset(org.mockito.ArgumentMatchers.eq("known@example.test"), any());
        verify(passwordResetService).requestReset(org.mockito.ArgumentMatchers.eq("unknown@example.test"), any());
    }

    @Test
    void passwordResetCompletionReturnsConfirmationWithoutCreatingASession() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token\",\"password\":\"new-password\","
                                + "\"confirmPassword\":\"new-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your password has been reset. Sign in with your new password."))
                .andExpect(request().sessionAttributeDoesNotExist("authenticatedUserId"));
        verify(passwordResetService).completeReset("token", "new-password", "new-password");
    }

    @Test
    void malformedResetEmailIsValidatedBeforeAsynchronousProcessing() throws Exception {
        when(passwordResetService.normalizeAndValidateEmail(any()))
                .thenThrow(new InvalidUserInputException("Enter a valid email address."));
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a valid email address."));
        org.mockito.Mockito.verify(passwordResetService, org.mockito.Mockito.never())
                .requestReset(any(), any());
    }
}
