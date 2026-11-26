package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.JdbcItemDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/** Owner-scoped, bounded library and snapshot/keyset activity reads. */
@Service
public class WorkspaceReadService {
    private final JdbcTemplate jdbc;
    private final JdbcItemDao items;
    public WorkspaceReadService(JdbcTemplate jdbc, JdbcItemDao items) { this.jdbc=jdbc; this.items=items; }

    public CatalogPage<CatalogItem> catalog(String query,Long categoryId,ItemType type,CatalogSort sort,ItemDifficulty difficulty,Integer maximumTime,String provider,int page,int pageSize) {
        return catalog(query,categoryId,type,sort,difficulty,maximumTime,provider,null,page,pageSize);
    }
    public CatalogPage<CatalogItem> catalog(String query,Long categoryId,ItemType type,CatalogSort sort,ItemDifficulty difficulty,Integer maximumTime,String provider,String topic,int page,int pageSize) {
        if(topic!=null && topic.length()>150)throw new InvalidUserInputException("Topic must be at most 150 characters.");
        if(page<1 || page>10000 || pageSize<1 || pageSize>100 || (categoryId!=null && categoryId<1) || (maximumTime!=null && (maximumTime<1 || maximumTime>1440)) || (query!=null && query.length()>120) || (provider!=null && provider.length()>160)) throw new InvalidUserInputException("Invalid catalog filters, page (1–10000), or page size (1–100).");
        return items.searchPage(query,categoryId,type,sort,difficulty,maximumTime,provider,topic,page,pageSize);
    }
    public List<CatalogItem> metadata(List<Long> ids) {
        if(ids==null || ids.isEmpty() || ids.size()>100 || ids.stream().anyMatch(id->id==null || id<1)) throw new InvalidUserInputException("Provide 1–100 valid resource IDs.");
        return items.findByIds(ids).stream().filter(CatalogItem::active).toList();
    }

    @Transactional(readOnly=true)
    public CatalogPage<CatalogItem> saved(long userId, int page, int pageSize, String query, Long collectionId) {
        if(page<1 || page>10000 || pageSize<1 || pageSize>100) throw new InvalidUserInputException("Page must be 1–10000 and page size 1–100.");
        String search=query==null?"":query.strip();
        if(search.length()>120) throw new InvalidUserInputException("Search must be at most 120 characters.");
        if(collectionId!=null && (collectionId<1 || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_collections WHERE collection_id=? AND user_id=?)",Boolean.class,collectionId,userId)))) throw new UserNotFoundException("Collection was not found.");
        List<Object> args=new ArrayList<>();
        String where;
        if(collectionId!=null) { where="EXISTS(SELECT 1 FROM user_collection_items ci JOIN user_collections c ON c.collection_id=ci.collection_id WHERE ci.item_id=i.item_id AND c.user_id=? AND c.collection_id=?)";args.add(userId);args.add(collectionId); }
        else { where="(EXISTS(SELECT 1 FROM interactions e WHERE e.user_id=? AND e.item_id=i.item_id AND e.interaction_type='SAVE') OR EXISTS(SELECT 1 FROM user_collection_items ci JOIN user_collections c ON c.collection_id=ci.collection_id WHERE ci.item_id=i.item_id AND c.user_id=?))";args.add(userId);args.add(userId); }
        if(!search.isBlank()) { where+=" AND (LOCATE(LOWER(?),LOWER(CONCAT_WS(' ',i.title,i.description,i.source_name,cat.name)))>0 OR EXISTS(SELECT 1 FROM item_interests ii JOIN interests t ON t.interest_id=ii.interest_id WHERE ii.item_id=i.item_id AND LOCATE(LOWER(?),LOWER(t.name))>0))";args.add(search);args.add(search); }
        String from=" FROM items i JOIN categories cat ON cat.category_id=i.category_id WHERE "+where;
        long total=jdbc.queryForObject("SELECT COUNT(*)"+from,Long.class,args.toArray());
        args.add(pageSize);args.add((page-1)*pageSize);
        var ids=jdbc.queryForList("SELECT i.item_id"+from+" ORDER BY i.created_at DESC,i.item_id DESC LIMIT ? OFFSET ?",Long.class,args.toArray());
        var metadata=items.findByIds(ids);
        return new CatalogPage<>(metadata,page,pageSize,total,(total+pageSize-1)/pageSize);
    }

    @Transactional
    public void unsave(long userId,long itemId) {
        if(itemId<1) throw new InvalidUserInputException("Resource ID must be positive.");
        jdbc.queryForObject("SELECT user_id FROM users WHERE user_id=? FOR UPDATE",Long.class,userId);
        jdbc.update("DELETE ci FROM user_collection_items ci JOIN user_collections c ON c.collection_id=ci.collection_id WHERE c.user_id=? AND ci.item_id=?",userId,itemId);
        jdbc.update("DELETE FROM interactions WHERE user_id=? AND item_id=? AND interaction_type='SAVE'",userId,itemId);
    }

    @Transactional(readOnly=true)
    public ActivityPage activity(long userId,int pageSize,InteractionType type,String cursor) {
        if(pageSize<1 || pageSize>100) throw new InvalidUserInputException("Page size must be 1–100.");
        long snapshot=jdbc.queryForObject("SELECT COALESCE(MAX(interaction_id),0) FROM interactions WHERE user_id=?",Long.class,userId);
        LocalDateTime before=null;long beforeId=0;
        String filter=type==null?"":type.name();
        if(cursor!=null) {
            try {
                if(cursor.length()>300) throw new IllegalArgumentException();
                String[] parts=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\\|",-1);
                if(parts.length!=5 || Long.parseLong(parts[0])!=userId || !parts[4].equals(filter)) throw new IllegalArgumentException();
                snapshot=Long.parseLong(parts[1]);before=LocalDateTime.parse(parts[2]);beforeId=Long.parseLong(parts[3]);
                if(snapshot<1 || beforeId<1 || beforeId>snapshot) throw new IllegalArgumentException();
            } catch(RuntimeException invalid) { throw new InvalidUserInputException("Invalid activity cursor or changed filter. Start a new page."); }
        }
        List<Object> args=new ArrayList<>(List.of(userId,snapshot));
        String where="user_id=? AND interaction_id<=?";
        if(type!=null){where+=" AND interaction_type=?";args.add(type.name());}
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM interactions WHERE "+where,Long.class,args.toArray());
        if(before!=null){where+=" AND (occurred_at < ? OR (occurred_at = ? AND interaction_id < ?))";args.add(before);args.add(before);args.add(beforeId);}
        args.add(pageSize+1);
        List<Interaction> rows=jdbc.query("SELECT interaction_id,user_id,item_id,interaction_type,occurred_at,search_query FROM interactions WHERE "+where+" ORDER BY occurred_at DESC,interaction_id DESC LIMIT ?",(rs,n)->new Interaction(rs.getLong("interaction_id"),rs.getLong("user_id"),rs.getObject("item_id",Long.class),InteractionType.valueOf(rs.getString("interaction_type")),rs.getTimestamp("occurred_at").toLocalDateTime(),rs.getString("search_query")),args.toArray());
        boolean more=rows.size()>pageSize;
        rows=rows.stream().limit(pageSize).toList();
        Map<Long,CatalogItem> resources=new HashMap<>();
        items.findByIds(rows.stream().map(Interaction::itemId).filter(Objects::nonNull).distinct().toList()).forEach(item->resources.put(item.itemId(),item));
        String next=null;
        if(more){Interaction last=rows.getLast();next=Base64.getUrlEncoder().withoutPadding().encodeToString((userId+"|"+snapshot+"|"+last.occurredAt()+"|"+last.interactionId()+"|"+filter).getBytes(StandardCharsets.UTF_8));}
        return new ActivityPage(rows.stream().map(row->new ActivityEntry(row.interactionId(),row.itemId(),row.type(),row.occurredAt(),row.searchQuery(),resources.get(row.itemId()))).toList(),next,more,total);
    }
    public record ActivityEntry(long interactionId,Long itemId,InteractionType type,LocalDateTime occurredAt,String searchQuery,CatalogItem resource) { }
    public record ActivityPage(List<ActivityEntry> items,String nextCursor,boolean hasMore,long totalItems) {
    public ActivityPage {
        items = items == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
    }
 }
}
