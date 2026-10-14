package com.guvi.recommendationsystem.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exports and resets only the signed-in user's personal recommendation data. */
@Service
public class UserDataService {
    private final JdbcTemplate jdbc;
    public UserDataService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> export(long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profile", jdbc.queryForMap("SELECT user_id, username, email, display_name, role, created_at FROM users WHERE user_id = ?", userId));
        result.put("preferences", jdbc.queryForList("SELECT category_id, preference_weight, preference_source, updated_at FROM user_preferences WHERE user_id = ? ORDER BY category_id", userId));
        result.put("interests", jdbc.queryForList("SELECT interest_id, interest_weight FROM user_interests WHERE user_id = ? ORDER BY interest_id", userId));
        result.put("topicSignals", jdbc.queryForList("SELECT interest_id, positive_score, negative_score, preference_score, confidence, trend, updated_at FROM user_topic_preferences WHERE user_id = ? ORDER BY interest_id", userId));
        result.put("onboarding", jdbc.queryForList("SELECT purpose, category_id, completed_at FROM user_onboarding WHERE user_id = ?", userId));
        result.put("interactions", jdbc.queryForList("SELECT interaction_id, item_id, interaction_type, search_query, occurred_at FROM interactions WHERE user_id = ? ORDER BY occurred_at", userId));
        result.put("feedback", jdbc.queryForList("SELECT item_id, rating, comment, created_at, updated_at FROM feedback WHERE user_id = ? ORDER BY item_id", userId));
        result.put("collections", jdbc.queryForList("SELECT c.collection_id, c.name, ci.item_id, ci.added_at FROM user_collections c LEFT JOIN user_collection_items ci ON ci.collection_id = c.collection_id WHERE c.user_id = ? ORDER BY c.name, ci.added_at", userId));
        result.put("exportedAt", java.time.Instant.now().toString());
        return result;
    }

    @Transactional
    public void resetPersonalization(long userId) {
        jdbc.update("DELETE FROM feedback WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM interactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_topic_preferences WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM recommendations WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM recommendation_batches WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_preferences WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_interests WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_onboarding_topics WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_onboarding WHERE user_id = ?", userId);
    }
}
