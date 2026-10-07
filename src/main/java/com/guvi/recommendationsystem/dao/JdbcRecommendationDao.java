package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Repository
public class JdbcRecommendationDao implements RecommendationDao {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RecommendationCandidate> candidateMapper = (resultSet, rowNumber) ->
            new RecommendationCandidate(
                    resultSet.getLong("item_id"),
                    resultSet.getString("title"),
                    resultSet.getBigDecimal("category_preference_weight"),
                    resultSet.getBigDecimal("interest_match"),
                    resultSet.getBigDecimal("user_average_rating"),
                    resultSet.getBigDecimal("item_average_rating"),
                    resultSet.getBigDecimal("positive_interaction_signal"),
                    resultSet.getBigDecimal("negative_interaction_signal"));

    private final RowMapper<PersonalizedRecommendation> recommendationMapper =
            (resultSet, rowNumber) -> new PersonalizedRecommendation(
                    resultSet.getLong("recommendation_id"),
                    resultSet.getObject("batch_id", Long.class),
                    resultSet.getLong("item_id"),
                    resultSet.getString("title"),
                    resultSet.getBigDecimal("score"),
                    resultSet.getString("reason"),
                    resultSet.getTimestamp("generated_at").toLocalDateTime());

    public JdbcRecommendationDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RecommendationCandidate> findCandidates(long userId) {
        return jdbcTemplate.query("""
                WITH user_ratings AS (
                    SELECT AVG(rating) AS average_rating
                    FROM feedback
                    WHERE user_id = ?
                ),
                item_ratings AS (
                    SELECT item_id, AVG(rating) AS average_rating
                    FROM feedback
                    GROUP BY item_id
                ),
                user_interest_totals AS (
                    SELECT SUM(interest_weight) AS total_weight
                    FROM user_interests user_interest
                    JOIN interests interest ON interest.interest_id = user_interest.interest_id
                    WHERE user_interest.user_id = ? AND interest.is_active = TRUE
                ),
                item_interest_matches AS (
                    SELECT item_interest.item_id,
                           SUM(user_interest.interest_weight * item_interest.relevance_weight) AS match_weight
                    FROM user_interests user_interest
                    JOIN item_interests item_interest
                      ON item_interest.interest_id = user_interest.interest_id
                    JOIN interests interest
                      ON interest.interest_id = user_interest.interest_id AND interest.is_active = TRUE
                    WHERE user_interest.user_id = ?
                    GROUP BY item_interest.item_id
                ),
                user_interaction_signals AS (
                    SELECT item_id,
                           SUM(CASE interaction_type
                               WHEN 'VIEW' THEN 0.15
                               WHEN 'CLICK' THEN 0.50
                               WHEN 'SAVE' THEN 1.00
                               WHEN 'PURCHASE' THEN 1.25
                               WHEN 'LIKE' THEN 1.00
                               ELSE 0.00
                           END) AS positive_signal,
                           SUM(CASE interaction_type
                               WHEN 'DISLIKE' THEN 1.25
                               WHEN 'HIDE' THEN 1.50
                               ELSE 0.00
                           END) AS negative_signal
                    FROM interactions
                    WHERE user_id = ?
                    GROUP BY item_id
                )
                SELECT item.item_id,
                       item.title,
                       preference.preference_weight AS category_preference_weight,
                       interest_match.match_weight / NULLIF(interest_total.total_weight, 0) AS interest_match,
                       user_ratings.average_rating AS user_average_rating,
                       item_ratings.average_rating AS item_average_rating,
                       COALESCE(interaction_signal.positive_signal, 0) AS positive_interaction_signal,
                       COALESCE(interaction_signal.negative_signal, 0) AS negative_interaction_signal
                FROM items item
                LEFT JOIN user_preferences preference
                  ON preference.category_id = item.category_id AND preference.user_id = ?
                LEFT JOIN item_interest_matches interest_match
                  ON interest_match.item_id = item.item_id
                CROSS JOIN user_interest_totals interest_total
                CROSS JOIN user_ratings
                LEFT JOIN item_ratings
                  ON item_ratings.item_id = item.item_id
                LEFT JOIN user_interaction_signals interaction_signal
                  ON interaction_signal.item_id = item.item_id
                WHERE item.is_active = TRUE
                ORDER BY item.item_id
                """,
                candidateMapper,
                userId,
                userId,
                userId,
                userId,
                userId);
    }

    @Override
    public long createBatch(long userId, String algorithmVersion) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO recommendation_batches (user_id, algorithm_version) VALUES (?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, algorithmVersion);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new recommendation batch ID.");
        }
        return generatedId.longValue();
    }

    @Override
    public PersonalizedRecommendation save(long userId, long batchId, RecommendationDraft recommendation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO recommendations (batch_id, user_id, item_id, score, reason, algorithm_version)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, batchId);
            statement.setLong(2, userId);
            statement.setLong(3, recommendation.itemId());
            statement.setBigDecimal(4, recommendation.score());
            statement.setString(5, recommendation.reason());
            statement.setString(6, "content-score-v1");
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new recommendation ID.");
        }
        return jdbcTemplate.query("""
                SELECT recommendation.recommendation_id, recommendation.batch_id,
                       recommendation.item_id, item.title,
                       recommendation.score, recommendation.reason, recommendation.generated_at
                FROM recommendations recommendation
                JOIN items item ON item.item_id = recommendation.item_id
                WHERE recommendation.recommendation_id = ?
                """, recommendationMapper, generatedId.longValue()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("The recommendation could not be read back."));
    }

    @Override
    public List<PersonalizedRecommendation> findLatestForUser(long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT recommendation.recommendation_id, recommendation.batch_id,
                       recommendation.item_id, item.title,
                recommendation.score, recommendation.reason, recommendation.generated_at
                FROM recommendations recommendation
                JOIN items item ON item.item_id = recommendation.item_id
                WHERE recommendation.user_id = ?
                  AND recommendation.batch_id <=> (
                      SELECT batch.batch_id
                      FROM recommendation_batches batch
                      WHERE batch.user_id = ?
                      ORDER BY batch.generated_at DESC, batch.batch_id DESC
                      LIMIT 1
                  )
                ORDER BY recommendation.generated_at DESC, recommendation.recommendation_id DESC
                LIMIT ?
                """, recommendationMapper, userId, userId, limit);
    }
}
