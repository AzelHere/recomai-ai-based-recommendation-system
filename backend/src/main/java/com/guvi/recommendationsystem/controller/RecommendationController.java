package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.service.RecommendationService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final SessionAccess sessionAccess;

    public RecommendationController(
            RecommendationService recommendationService,
            SessionAccess sessionAccess) {
        this.recommendationService = recommendationService;
        this.sessionAccess = sessionAccess;
    }

    public record ExplorationPreference(java.math.BigDecimal value) {}

    @GetMapping("/exploration")
    public ExplorationPreference exploration(HttpServletRequest request) {
        return new ExplorationPreference(java.math.BigDecimal.valueOf(
                recommendationService.exploration(sessionAccess.requireUserId(request))));
    }

    @org.springframework.web.bind.annotation.PutMapping("/exploration")
    public ResponseEntity<Void> updateExploration(
            @org.springframework.web.bind.annotation.RequestBody ExplorationPreference preference,
            HttpServletRequest request) {
        recommendationService.updateExploration(sessionAccess.requireUserId(request), preference.value());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<List<PersonalizedRecommendation>> generate(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        List<PersonalizedRecommendation> recommendations = recommendationService.generateForUser(
                sessionAccess.requireUserId(request),
                limit);
        return ResponseEntity.status(HttpStatus.CREATED).body(recommendations);
    }

    @GetMapping
    public List<PersonalizedRecommendation> getLatest(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        return recommendationService.getLatestForUser(sessionAccess.requireUserId(request), limit);
    }

    @PostMapping("/{recommendationId}/impression")
    public ResponseEntity<Void> recordImpression(@PathVariable long recommendationId, HttpServletRequest request) {
        recommendationService.recordImpression(sessionAccess.requireUserId(request), recommendationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{recommendationId}/click")
    public ResponseEntity<Void> recordClick(@PathVariable long recommendationId, HttpServletRequest request) {
        recommendationService.recordClick(sessionAccess.requireUserId(request), recommendationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/discovery")
    public List<CatalogItem> getCatalogDiscovery(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        return recommendationService.getCatalogDiscoveryForUser(sessionAccess.requireUserId(request), limit);
    }
}
