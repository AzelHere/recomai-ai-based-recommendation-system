package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.UserCollection;
import com.guvi.recommendationsystem.service.UserCollectionService;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users/me/collections")
public class UserCollectionController {
    private final UserCollectionService service;
    private final SessionAccess session;
    public UserCollectionController(UserCollectionService service, SessionAccess session) { this.service = service; this.session = session; }
    @GetMapping public List<UserCollection> list(HttpServletRequest request) { return service.list(session.requireUserId(request)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public UserCollection create(@RequestBody CollectionRequest body, HttpServletRequest request) {
        return service.create(session.requireUserId(request), body == null ? null : body.name());
    }
    @PutMapping("/{collectionId}/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable long collectionId, @PathVariable long itemId, HttpServletRequest request) {
        service.addItem(session.requireUserId(request), collectionId, itemId);
    }
    @DeleteMapping("/{collectionId}/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long collectionId, @PathVariable long itemId, HttpServletRequest request) {
        service.removeItem(session.requireUserId(request), collectionId, itemId);
    }
    @DeleteMapping("/{collectionId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long collectionId, HttpServletRequest request) { service.delete(session.requireUserId(request), collectionId); }
    public record CollectionRequest(String name) { }
}
