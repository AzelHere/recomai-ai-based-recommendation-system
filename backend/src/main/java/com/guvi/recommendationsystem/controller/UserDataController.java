package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.service.UserDataService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class UserDataController {
    private final UserDataService dataService;
    private final SessionAccess sessionAccess;
    public UserDataController(UserDataService dataService, SessionAccess sessionAccess) {
        this.dataService = dataService; this.sessionAccess = sessionAccess;
    }
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(HttpServletRequest request) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"recomai-my-data.json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(dataService.export(sessionAccess.requireUserId(request)));
    }
    @DeleteMapping("/personalization")
    public ResponseEntity<Void> resetPersonalization(HttpServletRequest request) {
        dataService.resetPersonalization(sessionAccess.requireUserId(request));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }
}
