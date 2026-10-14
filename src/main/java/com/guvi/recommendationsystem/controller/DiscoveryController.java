package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.InterestProfile;
import com.guvi.recommendationsystem.model.NicheOption;
import com.guvi.recommendationsystem.model.OnboardingRequest;
import com.guvi.recommendationsystem.model.OnboardingStatus;
import com.guvi.recommendationsystem.service.InterestProfileService;
import com.guvi.recommendationsystem.service.OnboardingService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DiscoveryController {
    private final OnboardingService onboardingService;
    private final InterestProfileService interestProfileService;
    private final SessionAccess sessionAccess;

    public DiscoveryController(OnboardingService onboardingService, InterestProfileService interestProfileService,
                               SessionAccess sessionAccess) {
        this.onboardingService = onboardingService;
        this.interestProfileService = interestProfileService;
        this.sessionAccess = sessionAccess;
    }

    @GetMapping("/discovery/niches")
    public List<NicheOption> getNiches() {
        return onboardingService.getOptions();
    }

    @GetMapping("/users/me/onboarding")
    public OnboardingStatus getOnboarding(HttpServletRequest request) {
        return onboardingService.getStatus(sessionAccess.requireUserId(request));
    }

    @PostMapping("/users/me/onboarding")
    public OnboardingStatus saveOnboarding(@RequestBody OnboardingRequest body, HttpServletRequest request) {
        return onboardingService.save(sessionAccess.requireUserId(request), body);
    }

    @GetMapping("/users/me/interests/insights")
    public InterestProfile getInterestInsights(HttpServletRequest request) {
        return interestProfileService.getForUser(sessionAccess.requireUserId(request));
    }
}
