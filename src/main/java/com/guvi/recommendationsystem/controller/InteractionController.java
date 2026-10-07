package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.TrackInteractionRequest;
import com.guvi.recommendationsystem.service.InteractionService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/interactions")
public class InteractionController {
    private final InteractionService interactionService;
    private final SessionAccess sessionAccess;

    public InteractionController(InteractionService interactionService, SessionAccess sessionAccess) {
        this.interactionService = interactionService;
        this.sessionAccess = sessionAccess;
    }

    @PostMapping
    public ResponseEntity<Interaction> track(
            @RequestBody TrackInteractionRequest interactionRequest,
            HttpServletRequest request) {
        Interaction interaction = interactionService.track(
                sessionAccess.requireUserId(request),
                interactionRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(interaction);
    }

    @GetMapping
    public List<Interaction> getRecent(
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        return interactionService.getRecentForUser(sessionAccess.requireUserId(request), limit);
    }
}
