package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.exception.UnauthorizedRequestException;
import com.guvi.recommendationsystem.model.LoginRequest;
import com.guvi.recommendationsystem.model.RegisterUserRequest;
import com.guvi.recommendationsystem.model.UserProfile;
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

    public AuthController(UserAccountService userAccountService, SessionAccess sessionAccess) {
        this.userAccountService = userAccountService;
        this.sessionAccess = sessionAccess;
    }

    @PostMapping("/register")
    public ResponseEntity<UserProfile> register(@RequestBody RegisterUserRequest request) {
        UserProfile profile = userAccountService.register(request);
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
}
