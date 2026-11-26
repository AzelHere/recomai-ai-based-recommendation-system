package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserCollection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class JdbcUserCollectionDao implements UserCollectionDao {
    private final JdbcTemplate jdbc;
    public JdbcUserCollectionDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<UserCollection> findByUserId(long userId) {
        return jdbc.query("SELECT c.collection_id,c.name,c.updated_at,ci.item_id FROM user_collections c LEFT JOIN user_collection_items ci ON ci.collection_id=c.collection_id WHERE c.user_id=? ORDER BY c.name,c.collection_id,ci.added_at DESC,ci.item_id", rs -> {
            var headers=new java.util.LinkedHashMap<Long,CollectionHeader>();
            var memberships=new java.util.HashMap<Long,java.util.List<Long>>();
            while(rs.next()) { long id=rs.getLong("collection_id");headers.putIfAbsent(id,new CollectionHeader(id,rs.getString("name"),rs.getTimestamp("updated_at").toLocalDateTime()));memberships.computeIfAbsent(id,ignored->new java.util.ArrayList<>());Long item=rs.getObject("item_id",Long.class);if(item!=null)memberships.get(id).add(item); }
            return headers.values().stream().map(h->new UserCollection(h.id(),h.name(),h.updatedAt(),memberships.get(h.id()))).toList();
        },userId);
    }
    @Override public boolean rename(long userId,long collectionId,String expectedName,String name) {
        return jdbc.update("UPDATE user_collections SET name=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND collection_id=? AND BINARY name=BINARY ?",name,userId,collectionId,expectedName)==1;
    }
    @Override public UserCollection create(long userId, String name) {
        jdbc.update("INSERT INTO user_collections (user_id, name) VALUES (?, ?)", userId, name);
        return jdbc.queryForObject("SELECT collection_id, name, updated_at FROM user_collections WHERE user_id = ? AND name = ?",
                (rs, row) -> new UserCollection(rs.getLong("collection_id"), rs.getString("name"),
                        rs.getTimestamp("updated_at").toLocalDateTime(), List.of()), userId, name);
    }
    @Override public boolean belongsToUser(long userId, long collectionId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_collections WHERE user_id = ? AND collection_id = ?",
                Integer.class, userId, collectionId);
        return count != null && count > 0;
    }
    @Override public void addItem(long collectionId, long itemId) {
        jdbc.update("INSERT INTO user_collection_items (collection_id, item_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE added_at = CURRENT_TIMESTAMP",
                collectionId, itemId);
    }
    @Override public void removeItem(long collectionId, long itemId) {
        jdbc.update("DELETE FROM user_collection_items WHERE collection_id = ? AND item_id = ?", collectionId, itemId);
    }
    @Override public void delete(long userId, long collectionId) {
        jdbc.update("DELETE FROM user_collections WHERE user_id = ? AND collection_id = ?", userId, collectionId);
    }
    private record CollectionHeader(long id, String name, java.time.LocalDateTime updatedAt) { }
}
