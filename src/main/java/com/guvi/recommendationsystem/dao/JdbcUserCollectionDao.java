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
        List<CollectionHeader> headers = jdbc.query(
                "SELECT collection_id, name, updated_at FROM user_collections WHERE user_id = ? ORDER BY name",
                (rs, row) -> new CollectionHeader(rs.getLong("collection_id"), rs.getString("name"),
                        rs.getTimestamp("updated_at").toLocalDateTime()), userId);
        return headers.stream().map(header -> new UserCollection(header.id(), header.name(), header.updatedAt(),
                findItemIds(header.id()))).toList();
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
    private List<Long> findItemIds(long collectionId) {
        return jdbc.query("SELECT item_id FROM user_collection_items WHERE collection_id = ? ORDER BY added_at DESC",
                (rs, row) -> rs.getLong(1), collectionId);
    }
    private record CollectionHeader(long id, String name, java.time.LocalDateTime updatedAt) { }
}
