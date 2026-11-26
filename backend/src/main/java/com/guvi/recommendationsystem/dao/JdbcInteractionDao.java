package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.InteractionType;
import com.guvi.recommendationsystem.model.InteractionWriteResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcInteractionDao implements InteractionDao {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Interaction> interactionMapper = (resultSet, rowNumber) -> new Interaction(
            resultSet.getLong("interaction_id"),
            resultSet.getLong("user_id"),
            resultSet.getObject("item_id", Long.class),
            InteractionType.valueOf(resultSet.getString("interaction_type")),
            resultSet.getTimestamp("occurred_at").toLocalDateTime(),
            resultSet.getString("search_query"));

    public JdbcInteractionDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Interaction insert(long userId, long itemId, InteractionType type) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            return com.guvi.recommendationsystem.dao.JdbcStatements.generated(connection, """
                    INSERT INTO interactions (user_id, item_id, interaction_type)
                    VALUES (?, ?, ?)
                    """, statement -> {
            statement.setLong(1, userId);
            statement.setLong(2, itemId);
            statement.setString(3, type.name());
            });
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new interaction ID.");
        }
        return findById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException("The new interaction could not be read back."));
    }

    @Override
    public InteractionWriteResult insertWithRequestId(long userId, long itemId, InteractionType type,
                                                       String requestId) {
        return insertWithRequestId(userId, itemId, type, null, requestId);
    }

    @Override
    public InteractionWriteResult insertSearchWithRequestId(long userId, String query, String requestId) {
        return insertWithRequestId(userId, null, InteractionType.SEARCH, query, requestId);
    }

    private InteractionWriteResult insertWithRequestId(long userId, Long itemId, InteractionType type,
                                                        String query, String requestId) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                return com.guvi.recommendationsystem.dao.JdbcStatements.generated(connection, """
                        INSERT INTO interactions (user_id, item_id, interaction_type, search_query, request_id)
                        VALUES (?, ?, ?, ?, ?)
                        """, statement -> {
                statement.setLong(1, userId);
                if (itemId == null) statement.setNull(2, java.sql.Types.BIGINT);
                else statement.setLong(2, itemId);
                statement.setString(3, type.name());
                statement.setString(4, query);
                statement.setString(5, requestId);
                });
            }, keyHolder);
            Number generatedId = keyHolder.getKey();
            if (generatedId == null) throw new IllegalStateException("The database did not return the new interaction ID.");
            return new InteractionWriteResult(findById(generatedId.longValue())
                    .orElseThrow(() -> new IllegalStateException("The new interaction could not be read back.")), true);
        } catch (DuplicateKeyException duplicate) {
            return findByUserAndRequestId(userId, requestId)
                    .map(existing -> new InteractionWriteResult(existing, false))
                    .orElseThrow(() -> duplicate);
        }
    }

    @Override
    public Interaction insertSearch(long userId, String query) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            return com.guvi.recommendationsystem.dao.JdbcStatements.generated(connection, """
                    INSERT INTO interactions (user_id, interaction_type, search_query)
                    VALUES (?, 'SEARCH', ?)
                    """, statement -> {
            statement.setLong(1, userId);
            statement.setString(2, query);
            });
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new search event ID.");
        }
        return findById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException("The new search event could not be read back."));
    }

    @Override
    public Optional<Interaction> findById(long interactionId) {
        return jdbcTemplate.query("""
                SELECT interaction_id, user_id, item_id, interaction_type, occurred_at, search_query
                FROM interactions
                WHERE interaction_id = ?
                """, interactionMapper, interactionId).stream().findFirst();
    }

    @Override
    public List<Interaction> findRecentByUser(long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT interaction_id, user_id, item_id, interaction_type, occurred_at, search_query
                FROM interactions
                WHERE user_id = ?
                ORDER BY occurred_at DESC, interaction_id DESC
                LIMIT ?
                """, interactionMapper, userId, limit);
    }

    @Override
    public List<Interaction> findCurrentProgressByUser(long userId) {
        return jdbcTemplate.query("""
                SELECT interaction_id, user_id, item_id, interaction_type, occurred_at, search_query
                FROM (
                    SELECT interaction_id, user_id, item_id, interaction_type, occurred_at, search_query,
                           ROW_NUMBER() OVER (PARTITION BY item_id
                               ORDER BY occurred_at DESC, interaction_id DESC) AS event_rank
                    FROM interactions
                    WHERE user_id = ? AND interaction_type IN ('READ_PROGRESS', 'COMPLETE')
                ) current_event
                WHERE event_rank = 1
                ORDER BY occurred_at DESC, interaction_id DESC
                """, interactionMapper, userId);
    }

    private Optional<Interaction> findByUserAndRequestId(long userId, String requestId) {
        return jdbcTemplate.query("""
                SELECT interaction_id, user_id, item_id, interaction_type, occurred_at, search_query
                FROM interactions WHERE user_id = ? AND request_id = ?
                """, interactionMapper, userId, requestId).stream().findFirst();
    }

    @Override
    public int deleteAllForUser(long userId) {
        return jdbcTemplate.update("DELETE FROM interactions WHERE user_id = ?", userId);
    }
}
