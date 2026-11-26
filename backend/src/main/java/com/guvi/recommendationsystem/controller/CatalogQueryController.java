package com.guvi.recommendationsystem.controller;
import com.guvi.recommendationsystem.model.*;
import com.guvi.recommendationsystem.service.WorkspaceReadService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/items")
public class CatalogQueryController {
    private final WorkspaceReadService service;
    public CatalogQueryController(WorkspaceReadService service){this.service=service;}
    @GetMapping("/search")
    public CatalogPage<CatalogItem> search(@RequestParam(required=false)String query,@RequestParam(required=false)Long categoryId,@RequestParam(required=false)ItemType type,@RequestParam(defaultValue="RELEVANCE")CatalogSort sort,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)ItemDifficulty difficulty,@RequestParam(required=false)Integer maximumTime,@RequestParam(required=false)String provider,@RequestParam(required=false)String topic){return service.catalog(query,categoryId,type,sort,difficulty,maximumTime,provider,topic,page,pageSize);}
    @GetMapping("/batch") public List<CatalogItem> metadata(@RequestParam List<Long> ids){return service.metadata(ids);}
}
