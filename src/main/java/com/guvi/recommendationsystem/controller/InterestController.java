package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.ActiveStatusRequest;
import com.guvi.recommendationsystem.model.Interest;
import com.guvi.recommendationsystem.model.InterestRequest;
import com.guvi.recommendationsystem.model.InterestWeightRequest;
import com.guvi.recommendationsystem.service.ItemInterestService;
import com.guvi.recommendationsystem.service.InterestService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InterestController {
    private final InterestService interestService;
    private final ItemInterestService itemInterestService;
    private final SessionAccess sessionAccess;

    public InterestController(
            InterestService interestService, ItemInterestService itemInterestService, SessionAccess sessionAccess) {
        this.interestService = interestService;
        this.itemInterestService = itemInterestService;
        this.sessionAccess = sessionAccess;
    }

    @GetMapping("/interests")
    public List<Interest> getActiveInterests() {
        return interestService.getActive();
    }

    @GetMapping("/admin/interests")
    public List<Interest> getAllInterests(HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return interestService.getAll();
    }

    @PostMapping("/admin/interests")
    public ResponseEntity<Interest> createInterest(
            @RequestBody InterestRequest interestRequest, HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        Interest created = interestService.create(interestRequest);
        return ResponseEntity.created(URI.create("/api/admin/interests/" + created.interestId())).body(created);
    }

    @PutMapping("/admin/interests/{interestId}")
    public Interest updateInterest(
            @PathVariable long interestId,
            @RequestBody InterestRequest interestRequest,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        return interestService.update(interestId, interestRequest);
    }

    @PatchMapping("/admin/interests/{interestId}/active")
    public Interest setInterestActive(
            @PathVariable long interestId,
            @RequestBody ActiveStatusRequest requestBody,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        if (requestBody == null || requestBody.active() == null) {
            throw new InvalidUserInputException("Active status is required.");
        }
        return requestBody.active()
                ? interestService.activate(interestId)
                : interestService.deactivate(interestId);
    }

    @PutMapping("/admin/items/{itemId}/interests/{interestId}")
    public ResponseEntity<Void> associateItemInterest(
            @PathVariable long itemId,
            @PathVariable long interestId,
            @RequestBody InterestWeightRequest body,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        itemInterestService.associate(itemId, interestId, body == null ? null : body.weight());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/items/{itemId}/interests/{interestId}")
    public ResponseEntity<Void> removeItemInterest(
            @PathVariable long itemId,
            @PathVariable long interestId,
            HttpServletRequest request) {
        sessionAccess.requireAdmin(request);
        itemInterestService.remove(itemId, interestId);
        return ResponseEntity.noContent().build();
    }
}
