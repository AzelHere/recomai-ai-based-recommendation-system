package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.*;
import com.guvi.recommendationsystem.service.WorkspaceReadService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class WorkspaceController {
    private final WorkspaceReadService service;
    private final SessionAccess session;
    public WorkspaceController(WorkspaceReadService service,SessionAccess session){this.service=service;this.session=session;}
    @GetMapping("/saved")
    public CatalogPage<CatalogItem> saved(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String query,@RequestParam(required=false)Long collectionId,HttpServletRequest request){return service.saved(session.requireUserId(request),page,pageSize,query,collectionId);}
    @DeleteMapping("/saved/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@PathVariable long itemId,HttpServletRequest request){service.unsave(session.requireUserId(request),itemId);}
    @GetMapping("/activity")
    public WorkspaceReadService.ActivityPage activity(@RequestParam(defaultValue="25")int pageSize,@RequestParam(required=false)InteractionType type,@RequestParam(required=false)String cursor,HttpServletRequest request){return service.activity(session.requireUserId(request),pageSize,type,cursor);}
}
