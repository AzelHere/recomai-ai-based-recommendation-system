package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.InteractionType;
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
public class JdbcInteractionDao implements InteractionDao {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Interaction> interactionMapper = (resultSet, rowNumber) -> new Interaction(
            resultSet.getLong("interaction_id"),
            resultSet.getLong("user_id"),
            resultSet.getLong("item_id"),
            InteractionType.valueOf(resultSet.getString("interaction_type")),
            resultSet.getTimestamp("occurred_at").toLocalDateTime());

    public JdbcInteractionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Interaction insert(long userId, long itemId, InteractionType type) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO interactions (user_id, item_id, interaction_type)
                    VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setLong(2, itemId);
            statement.setString(3, type.name());
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new interaction ID.");
        }
        return findById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException("The new interaction could not be read back."));
    }

    @Override
    public Optional<Interaction> findById(long interactionId) {
        return jdbcTemplate.query("""
                SELECT interaction_id, user_id, item_id, interaction_type, occurred_at
                FROM interactions
                WHERE interaction_id = ?
                """, interactionMapper, interactionId).stream().findFirst();
    }

    @Override
    public List<Interaction> findRecentByUser(long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT interaction_id, user_id, item_id, interaction_type, occurred_at
                FROM interactions
                WHERE user_id = ?
                ORDER BY occurred_at DESC, interaction_id DESC
                LIMIT ?
                """, interactionMapper, userId, limit);
    }
}
