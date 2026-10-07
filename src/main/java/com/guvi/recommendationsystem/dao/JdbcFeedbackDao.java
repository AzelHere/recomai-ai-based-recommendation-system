package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Feedback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcFeedbackDao implements FeedbackDao {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Feedback> mapper = (resultSet, rowNumber) -> new Feedback(
            resultSet.getLong("feedback_id"), resultSet.getLong("user_id"),
            resultSet.getLong("item_id"), resultSet.getInt("rating"),
            resultSet.getString("comment"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime());

    public JdbcFeedbackDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Feedback> findByUserId(long userId) {
        return jdbcTemplate.query("""
                SELECT feedback_id, user_id, item_id, rating, comment, created_at, updated_at
                FROM feedback WHERE user_id = ? ORDER BY updated_at DESC, feedback_id DESC
                """, mapper, userId);
    }

    @Override
    public Optional<Feedback> findByUserAndItem(long userId, long itemId) {
        return jdbcTemplate.query("""
                SELECT feedback_id, user_id, item_id, rating, comment, created_at, updated_at
                FROM feedback WHERE user_id = ? AND item_id = ?
                """, mapper, userId, itemId).stream().findFirst();
    }

    @Override
    public Feedback insert(long userId, long itemId, int rating, String comment) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO feedback (user_id, item_id, rating, comment) VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setLong(2, itemId);
            statement.setInt(3, rating);
            statement.setString(4, comment);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("The database did not return the new feedback ID.");
        }
        return jdbcTemplate.query("""
                SELECT feedback_id, user_id, item_id, rating, comment, created_at, updated_at
                FROM feedback WHERE feedback_id = ?
                """, mapper, id.longValue()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("The feedback could not be read back."));
    }

    @Override
    public boolean update(long userId, long itemId, int rating, String comment) {
        return jdbcTemplate.update("""
                UPDATE feedback SET rating = ?, comment = ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND item_id = ?
                """, rating, comment, userId, itemId) == 1;
    }
}
