package com.guvi.recommendationsystem.catalog;

import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class IngestionController {
    private final CatalogIngestionService service;
    private final CatalogMaintenance maintenance;
    private final SessionAccess sessions;
    public IngestionController(CatalogIngestionService service,CatalogMaintenance maintenance,SessionAccess sessions){this.service=service;this.maintenance=maintenance;this.sessions=sessions;}
    public record ImportRequest(List<EducationalResource> resources,String expectedSha256) { }
    @GetMapping("/items/{id}/provenance")
    public Map<String,Object> provenance(@PathVariable long id){return service.provenance(id);}
    @GetMapping("/admin/ingestion")
    public Map<String,Object> overview(HttpServletRequest request){sessions.requireAdmin(request);return Map.of("sources",service.sources(),"statistics",service.stats(),"batches",service.history(),"reviews",service.reviews(),"maintenance",maintenance.status());}
    @PostMapping("/admin/ingestion/preview")
    public CatalogIngestionService.Preview preview(@RequestBody ImportRequest input,HttpServletRequest request){sessions.requireAdmin(request);return service.preview(input.resources());}
    @PostMapping("/admin/ingestion/commit")
    public CatalogIngestionService.ImportResult commit(@RequestBody ImportRequest input,HttpServletRequest request){sessions.requireAdmin(request);return service.commit(input.resources(),input.expectedSha256());}
    @PostMapping("/admin/ingestion/check-urls")
    public Map<String,Object> check(HttpServletRequest request){sessions.requireAdmin(request);return maintenance.trigger();}
}
