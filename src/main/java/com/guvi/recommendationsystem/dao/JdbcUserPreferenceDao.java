package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserPreference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class JdbcUserPreferenceDao implements UserPreferenceDao {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserPreferenceDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UserPreference> findByUserId(long userId) {
        return jdbcTemplate.query("""
                SELECT category_id, preference_weight, updated_at
                FROM user_preferences
                WHERE user_id = ?
                ORDER BY category_id
                """,
                (resultSet, rowNumber) -> new UserPreference(
                        resultSet.getLong("category_id"),
                        resultSet.getBigDecimal("preference_weight"),
                        resultSet.getTimestamp("updated_at").toLocalDateTime()),
                userId);
    }

    @Override
    public void save(long userId, long categoryId, BigDecimal weight) {
        jdbcTemplate.update("""
                INSERT INTO user_preferences (user_id, category_id, preference_weight)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    preference_weight = ?,
                    updated_at = CURRENT_TIMESTAMP
                """,
                userId,
                categoryId,
                weight,
                weight);
    }
}
