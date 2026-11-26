package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.*;
import com.guvi.recommendationsystem.model.*;
import com.guvi.recommendationsystem.recommendation.RecommendationEngine;
import com.guvi.recommendationsystem.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Persists owner-scoped, bounded learning plans from existing catalog candidates.
 * User-row locks serialize creation and step changes; explicit progress events drive completion.
 * The resulting order is guidance, not a validated prerequisite curriculum. */
@Service
public class LearningJourneyService {
    private final com.guvi.recommendationsystem.recommendation.ContentFeatureScorer topicScorer=new com.guvi.recommendationsystem.recommendation.ContentFeatureScorer();
    private final JdbcTemplate jdbc;private final UserDao users;private final RecommendationDao candidates;
    private final RecommendationEngine ranker;private final JdbcItemDao resources;private final InteractionService interactions;
    public LearningJourneyService(JdbcTemplate jdbc,UserDao users,RecommendationDao candidates,RecommendationEngine ranker,JdbcItemDao resources,InteractionService interactions){this.jdbc=jdbc;this.users=users;this.candidates=candidates;this.ranker=ranker;this.resources=resources;this.interactions=interactions;}
    public record CreateRequest(String title,OnboardingPurpose goal,long categoryId,Integer steps,String requestId,String topic) { }
    public record Header(long journeyId,String title,OnboardingPurpose goal,Long categoryId,String status,String preferenceSnapshot,LocalDateTime createdAt,LocalDateTime updatedAt) { }
    public record Step(long stepId,int position,Long itemId,String title,String rationale,String status,LocalDateTime completedAt,boolean available,CatalogItem resource) { }
    public record Journey(Header journey,List<Step> steps,int completedSteps,int totalSteps,int completionPercent,Long currentStepId,List<Map<String,Object>> replacements) {
    public Journey {
        steps = steps == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(steps));
        replacements = replacements == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(replacements));
    }
 }
    private Header header(long userId,long id,boolean lock) {
        if(id<1)throw new InvalidUserInputException("Journey ID must be positive.");
        return jdbc.query("SELECT journey_id,title,goal,category_id,status,preference_snapshot,created_at,updated_at FROM learning_journeys WHERE user_id=? AND journey_id=?"+(lock?" FOR UPDATE":""),(rs,n)->mapHeader(rs),userId,id).stream().findFirst().orElseThrow(()->new UserNotFoundException("Journey was not found."));
    }
    private Header mapHeader(java.sql.ResultSet rs) throws java.sql.SQLException {return new Header(rs.getLong("journey_id"),rs.getString("title"),OnboardingPurpose.valueOf(rs.getString("goal")),rs.getObject("category_id",Long.class),rs.getString("status"),rs.getString("preference_snapshot"),rs.getTimestamp("created_at").toLocalDateTime(),rs.getTimestamp("updated_at").toLocalDateTime());}

    @Transactional(readOnly=true)
    public CatalogPage<Header> list(long userId,int page,int pageSize) {
        if(page<1 || page>10000 || pageSize<1 || pageSize>50)throw new InvalidUserInputException("Journey page must be 1–10000 and page size 1–50.");
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM learning_journeys WHERE user_id=?",Long.class,userId);
        var rows=jdbc.query("SELECT journey_id,title,goal,category_id,status,preference_snapshot,created_at,updated_at FROM learning_journeys WHERE user_id=? ORDER BY created_at DESC,journey_id DESC LIMIT ? OFFSET ?",(rs,n)->mapHeader(rs),userId,pageSize,(page-1)*pageSize);
        return new CatalogPage<>(rows,page,pageSize,total,(total+pageSize-1)/pageSize);
    }
    @Transactional(readOnly=true)
    public Journey get(long userId,long id) {
        Header head=header(userId,id,false);
        var raw=jdbc.queryForList("SELECT step_id,item_id,position,title_snapshot,rationale FROM learning_journey_steps WHERE journey_id=? ORDER BY position",id);
        var ids=raw.stream().map(row->(Number)row.get("item_id")).filter(Objects::nonNull).map(Number::longValue).toList();
        var metadata=new HashMap<Long,CatalogItem>();resources.findByIds(ids).forEach(item->metadata.put(item.itemId(),item));
        var progress=progress(userId,ids);var steps=new ArrayList<Step>();
        for(var row:raw) {
            Long itemId=row.get("item_id")==null?null:((Number)row.get("item_id")).longValue();CatalogItem resource=metadata.get(itemId);var event=itemId==null?null:progress.get(itemId);
            String status=event==null?"NOT_STARTED":event.type()==InteractionType.COMPLETE?"COMPLETED":"IN_PROGRESS";
            steps.add(new Step(((Number)row.get("step_id")).longValue(),((Number)row.get("position")).intValue(),itemId,(String)row.get("title_snapshot"),(String)row.get("rationale"),status,event!=null&&event.type()==InteractionType.COMPLETE?event.at():null,resource!=null&&resource.active(),resource));
        }
        int completed=(int)steps.stream().filter(s->s.status().equals("COMPLETED")).count();
        Long current=steps.stream().filter(s->!s.status().equals("COMPLETED")&&s.available()).map(Step::stepId).findFirst().orElse(null);
        var changes=jdbc.queryForList("SELECT r.step_id,r.previous_item_id,r.previous_title,r.previous_rationale,r.replaced_at FROM journey_step_replacements r JOIN learning_journey_steps s ON s.step_id=r.step_id WHERE s.journey_id=? ORDER BY r.replacement_id DESC LIMIT 100",id);
        return new Journey(head,List.copyOf(steps),completed,steps.size(),steps.isEmpty()?0:completed*100/steps.size(),current,changes);
    }
    private record Progress(InteractionType type,LocalDateTime at) { }
    private Map<Long,Progress> progress(long userId,List<Long> ids) {
        if(ids.isEmpty())return Map.of();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));var args=new ArrayList<Object>();args.add(userId);args.addAll(ids);
        Map<Long,Progress> result=new HashMap<>();
        jdbc.query("SELECT item_id,interaction_type,occurred_at FROM (SELECT item_id,interaction_type,occurred_at,ROW_NUMBER() OVER(PARTITION BY item_id ORDER BY occurred_at DESC,interaction_id DESC) rn FROM interactions WHERE user_id=? AND interaction_type IN ('READ_PROGRESS','COMPLETE') AND item_id IN ("+placeholders+")) p WHERE rn=1",(org.springframework.jdbc.core.RowCallbackHandler)rs->result.put(rs.getLong("item_id"),new Progress(InteractionType.valueOf(rs.getString("interaction_type")),rs.getTimestamp("occurred_at").toLocalDateTime())),args.toArray());
        return result;
    }

    @Transactional
    public Journey create(long userId,CreateRequest request) {
        if(request==null || request.title()==null || request.title().strip().isBlank() || request.title().strip().length()>150 || request.goal()==null || request.categoryId()<1 || request.requestId()==null || !request.requestId().matches("[A-Za-z0-9_-]{1,64}"))throw new InvalidUserInputException("A title (1–150 characters), supported goal, area and request ID are required.");
        String topic=request.topic()==null||request.topic().isBlank()?null:request.topic().strip();if(topic!=null&&topic.length()>120)throw new InvalidUserInputException("Topic focus must be at most 120 characters.");
        int count=request.steps()==null?6:request.steps();if(count<1||count>12)throw new InvalidUserInputException("Choose 1–12 journey steps.");
        users.lock(userId);
        String title=request.title().strip(),hash=hash(title+"|"+request.goal()+"|"+request.categoryId()+"|"+count+(topic==null?"":"|topic:"+topic));
        var existing=jdbc.queryForList("SELECT journey_id,request_hash FROM learning_journeys WHERE user_id=? AND request_id=?",userId,request.requestId());
        if(!existing.isEmpty()){if(!hash.equals(existing.getFirst().get("request_hash")))throw new UpdateConflictException("Request ID already belongs to a different journey request.");return get(userId,((Number)existing.getFirst().get("journey_id")).longValue());}
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM categories WHERE category_id=? AND is_active=TRUE AND niche_code IS NOT NULL)",Boolean.class,request.categoryId())))throw new InvalidUserInputException("Choose an available learning area.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM learning_journeys WHERE user_id=?",Integer.class,userId)>=100)throw new InvalidUserInputException("You can keep up to 100 journeys. Delete an unused journey before creating another.");
        var selected=select(userId,request.categoryId(),request.goal(),Set.of(),count,topic);
        if(selected.isEmpty())throw new InvalidUserInputException("No eligible resources remain for this area. Choose another area or return later.");
        String snapshot=jdbc.queryForObject("SELECT JSON_OBJECT('requestedTopic',?,'onboardingPurpose',(SELECT purpose FROM user_onboarding WHERE user_id=?),'onboardingCategoryId',(SELECT category_id FROM user_onboarding WHERE user_id=?),'interestIds',COALESCE((SELECT JSON_ARRAYAGG(interest_id) FROM user_interests WHERE user_id=?),JSON_ARRAY()))",String.class,topic,userId,userId,userId);
        var key=new GeneratedKeyHolder();
        jdbc.update(connection->{return com.guvi.recommendationsystem.dao.JdbcStatements.generated(connection, "INSERT INTO learning_journeys(user_id,title,goal,category_id,preference_snapshot,request_id,request_hash) VALUES(?,?,?,?,?,?,?)", stmt -> {stmt.setLong(1,userId);stmt.setString(2,title);stmt.setString(3,request.goal().name());stmt.setLong(4,request.categoryId());stmt.setString(5,snapshot);stmt.setString(6,request.requestId());stmt.setString(7,hash);});},key);
        long id=Objects.requireNonNull(key.getKey()).longValue();int position=1;
        for(var draft:selected)jdbc.update("INSERT INTO learning_journey_steps(journey_id,item_id,position,title_snapshot,rationale) VALUES(?,?,?,?,?)",id,draft.itemId(),position++,draft.itemTitle(),draft.reason()+" Suggested sequence using your profile and chosen area; prerequisites are not verified.");
        return get(userId,id);
    }
    private List<RecommendationDraft> select(long userId,long categoryId,OnboardingPurpose goal,Set<Long> excluded,int count,String topic) {
        String categoryName=jdbc.queryForObject("SELECT name FROM categories WHERE category_id=?",String.class,categoryId);
        var eligible=candidates.findCandidates(userId).stream().filter(c->!excluded.contains(c.itemId())&&categoryName.equalsIgnoreCase(c.categoryName())).toList();
        // Topic preselection bounds reranking work; preserve the ordinary no-focus generation path.
        if(topic!=null){
            var docs=jdbc.query("SELECT i.item_id,CONCAT_WS(' ',i.title,i.description,p.source_topics) feature_text FROM items i LEFT JOIN resource_provenance p ON p.item_id=i.item_id WHERE i.category_id=? AND i.is_active=TRUE ORDER BY i.item_id LIMIT 5000",(rs,n)->new com.guvi.recommendationsystem.recommendation.ContentFeatureScorer.ItemDocument(rs.getLong("item_id"),rs.getString("feature_text")),categoryId);
            var text=topicScorer.score(docs,Set.of(),topic);
            eligible=eligible.stream().filter(d->text.containsKey(d.itemId())).sorted(Comparator.comparingDouble((RecommendationCandidate d)->text.get(d.itemId()).score()).reversed()).limit(50).toList();
        }
        var ranked=ranker.recommend(eligible,50);
        var metadata=new HashMap<Long,CatalogItem>();resources.findByIds(ranked.stream().map(RecommendationDraft::itemId).toList()).forEach(item->metadata.put(item.itemId(),item));
        var completed=progress(userId,new ArrayList<>(metadata.keySet()));
        return ranked.stream().filter(d->{var item=metadata.get(d.itemId());return item!=null&&item.active()&&item.categoryId()==categoryId&&item.externalUrl()!=null&&!item.externalUrl().isBlank()&&!(completed.containsKey(d.itemId())&&completed.get(d.itemId()).type()==InteractionType.COMPLETE);})
                .sorted(Comparator.comparingInt((RecommendationDraft d)->goalPriority(metadata.get(d.itemId()),goal)).thenComparingInt(d->difficultyOrder(metadata.get(d.itemId()).difficulty())))
                .limit(count).toList();
    }
    static int difficultyOrder(ItemDifficulty value){return value==ItemDifficulty.BEGINNER?0:value==ItemDifficulty.ALL_LEVELS?1:value==ItemDifficulty.INTERMEDIATE?2:value==ItemDifficulty.UNKNOWN?4:3;}
    static int goalPriority(CatalogItem item,OnboardingPurpose goal){return switch(goal){case LEARN -> Set.of(ItemType.COURSE,ItemType.GUIDE,ItemType.DOCUMENTATION,ItemType.ARTICLE).contains(item.type())?0:1;case CAREER -> item.tags().stream().anyMatch(t->Set.of("Career Development","Interview Preparation","Professional Skills").contains(t))?0:1;default -> 0;};}
    private String hash(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}

    @Transactional
    public Journey updateStep(long userId,long id,long stepId,String status) {
        if(!Set.of("IN_PROGRESS","COMPLETED").contains(status==null?"":status))throw new InvalidUserInputException("Choose IN_PROGRESS or COMPLETED. Reopening uses IN_PROGRESS.");
        users.lock(userId);Header head=header(userId,id,true);if(!head.status().equals("ACTIVE"))throw new UpdateConflictException("Archived journeys are read-only.");
        Step step=get(userId,id).steps().stream().filter(s->s.stepId()==stepId).findFirst().orElseThrow(()->new UserNotFoundException("Journey step was not found."));
        if(step.status().equals(status))return get(userId,id);
        if(!step.available())throw new UpdateConflictException("This resource is unavailable. Replace unavailable steps before continuing.");
        interactions.track(userId,new TrackInteractionRequest(step.itemId(),status.equals("COMPLETED")?InteractionType.COMPLETE:InteractionType.READ_PROGRESS));
        jdbc.update("UPDATE learning_journeys SET updated_at=CURRENT_TIMESTAMP WHERE journey_id=?",id);
        return get(userId,id);
    }
    @Transactional
    public Journey archive(long userId,long id){users.lock(userId);header(userId,id,true);jdbc.update("UPDATE learning_journeys SET status='ARCHIVED' WHERE user_id=? AND journey_id=?",userId,id);return get(userId,id);}
    @Transactional
    public void delete(long userId,long id){users.lock(userId);header(userId,id,true);jdbc.update("DELETE FROM learning_journeys WHERE user_id=? AND journey_id=?",userId,id);}
    @Transactional
    public Journey replaceUnavailable(long userId,long id) {
        users.lock(userId);Header head=header(userId,id,true);if(!head.status().equals("ACTIVE"))throw new UpdateConflictException("Archived journeys are read-only.");
        Journey journey=get(userId,id);var unavailable=journey.steps().stream().filter(s->!s.available()&&!s.status().equals("COMPLETED")).toList();if(unavailable.isEmpty())return journey;
        if(head.categoryId()==null)throw new UpdateConflictException("The original area is unavailable. Your journey is preserved; create a new one in another area.");
        var excluded=new HashSet<Long>();journey.steps().stream().map(Step::itemId).filter(Objects::nonNull).forEach(excluded::add);
        var replacements=select(userId,head.categoryId(),head.goal(),excluded,unavailable.size(),tools.jackson.databind.json.JsonMapper.builder().build().readTree(head.preferenceSnapshot()).path("requestedTopic").asString(null));
        if(replacements.isEmpty())throw new UpdateConflictException("No suitable replacements are available. Your existing steps are preserved.");
        for(int i=0;i<replacements.size();i++){var old=unavailable.get(i);var next=replacements.get(i);jdbc.update("INSERT INTO journey_step_replacements(step_id,previous_item_id,previous_title,previous_rationale) VALUES(?,?,?,?)",old.stepId(),old.itemId(),old.title(),old.rationale());jdbc.update("UPDATE learning_journey_steps SET item_id=?,title_snapshot=?,rationale=? WHERE step_id=?",next.itemId(),next.itemTitle(),next.reason()+" Replaces an unavailable, uncompleted step; prior reference retained in history.",old.stepId());}
        jdbc.update("UPDATE learning_journeys SET updated_at=CURRENT_TIMESTAMP WHERE journey_id=?",id);return get(userId,id);
    }
}
