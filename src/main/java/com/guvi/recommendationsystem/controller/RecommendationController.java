package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
import com.guvi.recommendationsystem.model.CatalogItem;
import com.guvi.recommendationsystem.service.RecommendationService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/discovery")
    public List<CatalogItem> getCatalogDiscovery(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        return recommendationService.getCatalogDiscoveryForUser(sessionAccess.requireUserId(request), limit);
    }
}
