package com.guvi.recommendationsystem.controller;
import com.guvi.recommendationsystem.service.LearningJourneyService;
import com.guvi.recommendationsystem.model.CatalogPage;
import com.guvi.recommendationsystem.web.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/journeys")
public class LearningJourneyController {
    private final LearningJourneyService service;private final SessionAccess session;
    public LearningJourneyController(LearningJourneyService service,SessionAccess session){this.service=service;this.session=session;}
    @GetMapping public CatalogPage<LearningJourneyService.Header> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,HttpServletRequest r){return service.list(session.requireUserId(r),page,pageSize);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public LearningJourneyService.Journey create(@RequestBody LearningJourneyService.CreateRequest body,HttpServletRequest r){return service.create(session.requireUserId(r),body);}
    @GetMapping("/{id}") public LearningJourneyService.Journey get(@PathVariable long id,HttpServletRequest r){return service.get(session.requireUserId(r),id);}
    @PatchMapping("/{id}/steps/{stepId}") public LearningJourneyService.Journey step(@PathVariable long id,@PathVariable long stepId,@RequestBody StepRequest body,HttpServletRequest r){return service.updateStep(session.requireUserId(r),id,stepId,body==null?null:body.status());}
    @PostMapping("/{id}/archive") public LearningJourneyService.Journey archive(@PathVariable long id,HttpServletRequest r){return service.archive(session.requireUserId(r),id);}
    @PostMapping("/{id}/replace-unavailable") public LearningJourneyService.Journey replace(@PathVariable long id,HttpServletRequest r){return service.replaceUnavailable(session.requireUserId(r),id);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id,HttpServletRequest r){service.delete(session.requireUserId(r),id);}
    public record StepRequest(String status) { }
}
