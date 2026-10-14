package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.DisplayNameUpdateRequest;
import com.guvi.recommendationsystem.model.PreferenceWeightRequest;
import com.guvi.recommendationsystem.model.UserPreference;
import com.guvi.recommendationsystem.model.UserProfile;
import com.guvi.recommendationsystem.model.RecommendationProfile;
import com.guvi.recommendationsystem.model.UserInterestRequest;
import com.guvi.recommendationsystem.service.UserAccountService;
import com.guvi.recommendationsystem.service.UserInterestService;
import com.guvi.recommendationsystem.service.RecommendationProfileService;
import com.guvi.recommendationsystem.service.UserPreferenceService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final UserAccountService userAccountService;
    private final UserPreferenceService userPreferenceService;
    private final UserInterestService userInterestService;
    private final RecommendationProfileService recommendationProfileService;
    private final SessionAccess sessionAccess;

    public UserController(
            UserAccountService userAccountService,
            UserPreferenceService userPreferenceService,
            UserInterestService userInterestService,
            RecommendationProfileService recommendationProfileService,
            SessionAccess sessionAccess) {
        this.userAccountService = userAccountService;
        this.userPreferenceService = userPreferenceService;
        this.userInterestService = userInterestService;
        this.recommendationProfileService = recommendationProfileService;
        this.sessionAccess = sessionAccess;
    }

    @GetMapping
    public UserProfile getProfile(HttpServletRequest request) {
        return userAccountService.getProfile(sessionAccess.requireUserId(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(HttpServletRequest request) {
        long userId = sessionAccess.requireUserId(request);
        userAccountService.deleteAccount(userId);
        sessionAccess.endSession(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recommendation-profile")
    public RecommendationProfile getRecommendationProfile(HttpServletRequest request) {
        return recommendationProfileService.getForUser(sessionAccess.requireUserId(request));
    }

    @PatchMapping("/profile")
    public UserProfile updateProfile(
            @RequestBody DisplayNameUpdateRequest requestBody,
            HttpServletRequest request) {
        String displayName = requestBody == null ? null : requestBody.displayName();
        return userAccountService.updateDisplayName(sessionAccess.requireUserId(request), displayName);
    }

    @GetMapping("/preferences")
    public List<UserPreference> getPreferences(HttpServletRequest request) {
        return userPreferenceService.getPreferences(sessionAccess.requireUserId(request));
    }

    @PutMapping("/preferences/{categoryId}")
    public ResponseEntity<Void> setPreference(
            @PathVariable long categoryId,
            @RequestBody PreferenceWeightRequest requestBody,
            HttpServletRequest request) {
        userPreferenceService.setPreference(
                sessionAccess.requireUserId(request),
                categoryId,
                requestBody == null ? null : requestBody.weight());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/interests/{interestId}")
    public ResponseEntity<Void> selectInterest(
            @PathVariable long interestId,
            @RequestBody UserInterestRequest requestBody,
            HttpServletRequest request) {
        userInterestService.select(sessionAccess.requireUserId(request), interestId,
                requestBody == null ? null : requestBody.weight());
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/interests/{interestId}")
    public ResponseEntity<Void> removeInterest(@PathVariable long interestId, HttpServletRequest request) {
        userInterestService.remove(sessionAccess.requireUserId(request), interestId);
        return ResponseEntity.noContent().build();
    }
}
