package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.AdminSettingsAuditEntry;
import com.guvi.recommendationsystem.model.AdminSettingsPreviewRequest;
import com.guvi.recommendationsystem.model.AdminSettingsUpdateRequest;
import com.guvi.recommendationsystem.model.AdminSystemSettings;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import com.guvi.recommendationsystem.service.AdminSettingsService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {
    private final AdminSettingsService service;
    private final SessionAccess session;
    public AdminSettingsController(AdminSettingsService service, SessionAccess session) { this.service = service; this.session = session; }

    @GetMapping public AdminSystemSettings get(HttpServletRequest request) {
        session.requireAdmin(request); return service.getSettings();
    }
    @PutMapping public AdminSystemSettings update(@RequestBody AdminSettingsUpdateRequest body, HttpServletRequest request) {
        return service.update(body, session.requireAdmin(request));
    }
    @PostMapping("/preview") public List<RecommendationDraft> preview(@RequestBody AdminSettingsPreviewRequest body,
                                                                         HttpServletRequest request) {
        session.requireAdmin(request); return service.preview(body);
    }
    @GetMapping("/audit") public List<AdminSettingsAuditEntry> audit(@RequestParam(defaultValue = "25") int limit,
                                                                       HttpServletRequest request) {
        session.requireAdmin(request); return service.getAudit(limit);
    }
}
