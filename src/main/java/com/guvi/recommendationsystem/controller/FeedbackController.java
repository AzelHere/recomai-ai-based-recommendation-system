package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.Feedback;
import com.guvi.recommendationsystem.model.FeedbackRequest;
import com.guvi.recommendationsystem.service.FeedbackService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;
    private final SessionAccess sessionAccess;

    public FeedbackController(FeedbackService feedbackService, SessionAccess sessionAccess) {
        this.feedbackService = feedbackService;
        this.sessionAccess = sessionAccess;
    }

    @GetMapping
    public List<Feedback> getMyFeedback(HttpServletRequest request) {
        return feedbackService.getForUser(sessionAccess.requireUserId(request));
    }

    @PostMapping
    public ResponseEntity<Feedback> createFeedback(
            @RequestBody FeedbackRequest body, HttpServletRequest request) {
        Feedback feedback = feedbackService.create(sessionAccess.requireUserId(request), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(feedback);
    }

    @PutMapping("/{itemId}")
    public Feedback updateFeedback(
            @PathVariable long itemId,
            @RequestBody FeedbackRequest body,
            HttpServletRequest request) {
        return feedbackService.update(sessionAccess.requireUserId(request), itemId, body);
    }
}
