package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.exception.UnauthorizedRequestException;
import com.guvi.recommendationsystem.model.LoginRequest;
import com.guvi.recommendationsystem.model.RegisterUserRequest;
import com.guvi.recommendationsystem.model.UserProfile;
import com.guvi.recommendationsystem.model.PasswordResetRequest;
import com.guvi.recommendationsystem.model.PasswordResetCompletionRequest;
import com.guvi.recommendationsystem.service.PasswordResetService;
import com.guvi.recommendationsystem.web.RequestContextFilter;
import com.guvi.recommendationsystem.service.UserAccountService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountService userAccountService;
    private final SessionAccess sessionAccess;
    private final PasswordResetService passwordResetService;

    public AuthController(UserAccountService userAccountService, SessionAccess sessionAccess,
                          PasswordResetService passwordResetService) {
        this.userAccountService = userAccountService;
        this.sessionAccess = sessionAccess;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserProfile> register(
            @RequestBody RegisterUserRequest request,
            HttpServletRequest servletRequest) {
        UserProfile profile = userAccountService.register(request);
        sessionAccess.startSession(servletRequest, profile);
        return ResponseEntity.status(HttpStatus.CREATED).body(profile);
    }

    @PostMapping("/login")
    public UserProfile login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        UserProfile profile = userAccountService.authenticate(request)
                .orElseThrow(() -> new UnauthorizedRequestException("Email or password is incorrect."));
        sessionAccess.startSession(servletRequest, profile);
        return profile;
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        sessionAccess.endSession(request);
        return ResponseEntity.ok(Map.of("message", "Signed out."));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @RequestBody PasswordResetRequest request, HttpServletRequest servletRequest) {
        String normalizedEmail = passwordResetService.normalizeAndValidateEmail(request == null ? null : request.email());
        passwordResetService.requestReset(normalizedEmail,
                String.valueOf(servletRequest.getAttribute(RequestContextFilter.REQUEST_ID_ATTRIBUTE)));
        return ResponseEntity.accepted().body(Map.of("message", PasswordResetService.REQUEST_MESSAGE));
    }

    @PostMapping("/password-reset/complete")
    public ResponseEntity<Map<String, String>> completePasswordReset(
            @RequestBody PasswordResetCompletionRequest request) {
        passwordResetService.completeReset(request == null ? null : request.token(),
                request == null ? null : request.password(),
                request == null ? null : request.confirmPassword());
        return ResponseEntity.ok(Map.of("message", "Your password has been reset. Sign in with your new password."));
    }
}
