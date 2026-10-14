package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.InteractionType;
import com.guvi.recommendationsystem.model.PersonalizedRecommendation;
import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import com.guvi.recommendationsystem.recommendation.ContentFeatureScorer;
import com.guvi.recommendationsystem.service.InteractionSignalWeights;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class JdbcRecommendationDao implements RecommendationDao {
    private final JdbcTemplate jdbcTemplate;
    private final InteractionSignalWeights signalWeights;
    private final ContentFeatureScorer contentFeatureScorer = new ContentFeatureScorer();

    private final RowMapper<RecommendationCandidate> candidateMapper = (resultSet, rowNumber) ->
            new RecommendationCandidate(resultSet.getLong("item_id"), resultSet.getString("title"),
                    resultSet.getBigDecimal("category_preference_weight"), resultSet.getBigDecimal("interest_match"),
                    resultSet.getBigDecimal("user_average_rating"), resultSet.getBigDecimal("item_average_rating"),
                    resultSet.getBigDecimal("positive_interaction_signal"),
                    resultSet.getBigDecimal("negative_interaction_signal"), resultSet.getBigDecimal("quality_score"),
                    resultSet.getString("category_name"), resultSet.getString("matched_topics"),
                    resultSet.getBigDecimal("related_interest_match"), resultSet.getBigDecimal("purpose_match"),
                    BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, "",
                    resultSet.getBoolean("is_editor_pick"));

    private final RowMapper<PersonalizedRecommendation> recommendationMapper =
            (resultSet, rowNumber) -> new PersonalizedRecommendation(
                    resultSet.getLong("recommendation_id"), resultSet.getObject("batch_id", Long.class),
                    resultSet.getLong("item_id"), resultSet.getString("title"), resultSet.getBigDecimal("score"),
                    resultSet.getString("reason"), resultSet.getString("reason_type"),
                    resultSet.getString("bucket_type"), resultSet.getTimestamp("generated_at").toLocalDateTime(),
                    readBreakdown(resultSet.getString("explanation_json")));

    public JdbcRecommendationDao(JdbcTemplate jdbcTemplate, InteractionSignalWeights signalWeights) {
        this.jdbcTemplate = jdbcTemplate;
        this.signalWeights = signalWeights;
    }

    @Override
    public List<RecommendationCandidate> findCandidates(long userId) {
        String query = """
                WITH user_category_ratings AS (
                    SELECT item.category_id, AVG(feedback.rating) AS average_rating
                    FROM feedback JOIN items item ON item.item_id = feedback.item_id
                    WHERE feedback.user_id = ? GROUP BY item.category_id
                ),
                user_behavior_maturity AS (
                    SELECT 1.0 - EXP(-COALESCE(SUM(interaction_count), 0) / 10.0) AS strength
                    FROM user_topic_preferences WHERE user_id = ?
                ),
                user_topic_behavior_maturity AS (
                    SELECT interest_id, 1.0 - EXP(-interaction_count / 10.0) AS strength
                    FROM user_topic_preferences WHERE user_id = ?
                ),
                item_ratings AS (
                    -- The user's own rating is profile evidence, not independent catalog evidence.
                    SELECT item_id, AVG(rating) AS average_rating FROM feedback
                    WHERE user_id <> ? GROUP BY item_id
                ),
                item_interest_matches AS (
                    SELECT item_interest.item_id,
                           SUM(GREATEST(COALESCE(user_interest.interest_weight
                                   * CASE WHEN onboarding_topic.user_id IS NULL THEN 1.0
                                          ELSE 1.0 - COALESCE(topic_maturity.strength, 0) END
                                   * COALESCE(POW(0.5,
                                   GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR, onboarding_topic.selected_at, CURRENT_TIMESTAMP), 0)) / (24.0 * 60)), 1), 0), COALESCE(
                               topic.preference_score * POW(0.5, GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR, topic.last_interacted_at,
                                   CURRENT_TIMESTAMP), 0)) / (24.0 * 45)), 0))
                               * item_interest.relevance_weight) AS match_weight,
                           GROUP_CONCAT(DISTINCT interest.name ORDER BY interest.name SEPARATOR ', ') AS topic_names
                    FROM item_interests item_interest
                    JOIN interests interest ON interest.interest_id = item_interest.interest_id AND interest.is_active = TRUE
                    LEFT JOIN user_interests user_interest
                      ON user_interest.interest_id = interest.interest_id AND user_interest.user_id = ?
                    LEFT JOIN user_topic_preferences topic
                      ON topic.interest_id = interest.interest_id AND topic.user_id = ?
                    LEFT JOIN user_onboarding_topics onboarding_topic
                      ON onboarding_topic.interest_id = interest.interest_id AND onboarding_topic.user_id = ?
                    LEFT JOIN user_topic_behavior_maturity topic_maturity
                      ON topic_maturity.interest_id = interest.interest_id
                    WHERE user_interest.user_id IS NOT NULL OR topic.user_id IS NOT NULL
                    GROUP BY item_interest.item_id
                ),
                related_topic_matches AS (
                    SELECT target_item.item_id,
                           SUM(GREATEST(COALESCE(user_interest.interest_weight
                                   * CASE WHEN onboarding_topic.user_id IS NULL THEN 1.0
                                          ELSE 1.0 - COALESCE(topic_maturity.strength, 0) END
                                   * COALESCE(POW(0.5,
                                   GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR, onboarding_topic.selected_at, CURRENT_TIMESTAMP), 0)) / (24.0 * 60)), 1), 0),
                               COALESCE(topic.preference_score * POW(0.5,
                                   GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR, topic.last_interacted_at, CURRENT_TIMESTAMP), 0)) / (24.0 * 45)), 0))
                               * relation.relevance_weight * target_item.relevance_weight) AS match_weight
                    FROM interest_relations relation
                    LEFT JOIN user_interests user_interest
                      ON user_interest.interest_id = relation.interest_id AND user_interest.user_id = ?
                    LEFT JOIN user_topic_preferences topic
                      ON topic.interest_id = relation.interest_id AND topic.user_id = ?
                    LEFT JOIN user_onboarding_topics onboarding_topic
                      ON onboarding_topic.interest_id = relation.interest_id AND onboarding_topic.user_id = ?
                    LEFT JOIN user_topic_behavior_maturity topic_maturity
                      ON topic_maturity.interest_id = relation.interest_id
                    JOIN item_interests target_item ON target_item.interest_id = relation.related_interest_id
                    WHERE user_interest.user_id IS NOT NULL OR topic.user_id IS NOT NULL
                    GROUP BY target_item.item_id
                ),
                negative_topic_matches AS (
                    SELECT item_interest.item_id,
                           SUM(GREATEST(0, -topic.preference_score * POW(0.5, GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR,
                               topic.last_interacted_at, CURRENT_TIMESTAMP), 0)) / (24.0 * 45)))
                               * item_interest.relevance_weight) AS match_weight
                    FROM user_topic_preferences topic
                    JOIN item_interests item_interest ON item_interest.interest_id = topic.interest_id
                    WHERE topic.user_id = ?
                    GROUP BY item_interest.item_id
                ),
                user_interaction_signals AS (
                    SELECT item_id,
                           SUM((CASE interaction_type
                               WHEN 'SAVE' THEN ? WHEN 'PURCHASE' THEN ? WHEN 'LIKE' THEN ? WHEN 'SHARE' THEN ?
                               ELSE 0 END) * POW(0.5, GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR, occurred_at, CURRENT_TIMESTAMP), 0)) / (24.0 * 45))) AS positive_signal
                    FROM interactions WHERE user_id = ? GROUP BY item_id
                )
                SELECT item.item_id, item.title, category.name AS category_name,
                       CASE WHEN preference.preference_source = 'ONBOARDING'
                            THEN preference.preference_weight * COALESCE(POW(0.5, GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR,
                                 onboarding.completed_at, CURRENT_TIMESTAMP), 0)) / (24.0 * 60)), 1)
                            ELSE preference.preference_weight END
                           AS category_preference_weight,
                       LEAST(COALESCE(interest_match.match_weight, 0), 1.0) AS interest_match,
                       LEAST(COALESCE(related_match.match_weight, 0), 1.0) AS related_interest_match,
                       interest_match.topic_names AS matched_topics,
                       CASE
                         WHEN onboarding.purpose = 'LEARN' AND item.item_type IN ('COURSE', 'GUIDE', 'DOCUMENTATION') THEN 1.0
                         WHEN onboarding.purpose = 'CAREER' AND EXISTS (
                             SELECT 1 FROM item_interests purpose_item_interest
                             JOIN interests purpose_interest ON purpose_interest.interest_id = purpose_item_interest.interest_id
                             WHERE purpose_item_interest.item_id = item.item_id
                               AND purpose_interest.name IN ('Career Development', 'Interview Preparation', 'Professional Skills')
                         ) THEN 1.0
                         WHEN onboarding.purpose = 'IMPROVE_MYSELF'
                           AND category.niche_code IN ('HEALTH_FITNESS', 'LEARNING_CAREER', 'CREATIVE_LIFESTYLE') THEN 0.60
                         WHEN onboarding.purpose = 'EXPLORE' THEN 0.30
                         ELSE 0.0
                       END * (1.0 - maturity.strength) AS purpose_match,
                       user_ratings.average_rating AS user_average_rating,
                       item_ratings.average_rating AS item_average_rating,
                       COALESCE(interaction_signal.positive_signal, 0) AS positive_interaction_signal,
                       LEAST(COALESCE(negative_topic.match_weight, 0), 1.0) AS negative_interaction_signal,
                       item.quality_score, item.is_editor_pick
                FROM items item
                JOIN categories category ON category.category_id = item.category_id
                LEFT JOIN user_preferences preference
                  ON preference.category_id = item.category_id AND preference.user_id = ?
                LEFT JOIN user_onboarding onboarding ON onboarding.user_id = preference.user_id
                CROSS JOIN user_behavior_maturity maturity
                LEFT JOIN item_interest_matches interest_match ON interest_match.item_id = item.item_id
                LEFT JOIN related_topic_matches related_match ON related_match.item_id = item.item_id
                LEFT JOIN negative_topic_matches negative_topic ON negative_topic.item_id = item.item_id
                LEFT JOIN user_category_ratings user_ratings ON user_ratings.category_id = item.category_id
                LEFT JOIN item_ratings ON item_ratings.item_id = item.item_id
                LEFT JOIN user_interaction_signals interaction_signal ON interaction_signal.item_id = item.item_id
                WHERE item.is_active = TRUE AND category.is_active = TRUE
                  AND NOT EXISTS (
                      SELECT 1 FROM interactions recent_exposure
                      WHERE recent_exposure.user_id = ? AND recent_exposure.item_id = item.item_id
                        AND recent_exposure.interaction_type IN ('VIEW', 'CLICK', 'READ_PROGRESS')
                        AND recent_exposure.occurred_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 14 DAY)
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM interactions rejection
                      WHERE rejection.user_id = ? AND rejection.item_id = item.item_id
                        AND rejection.interaction_type IN ('DISLIKE', 'HIDE')
                  )
                ORDER BY (12 * COALESCE(category_preference_weight, 0)
                        + 24 * COALESCE(interest_match, 0)
                        + 10 * COALESCE(related_interest_match, 0)
                        + 16 * COALESCE(positive_interaction_signal, 0)
                        - 20 * COALESCE(negative_interaction_signal, 0)
                        + 2 * COALESCE(item.quality_score, 0)
                        + 5 * item.is_editor_pick) DESC, item.item_id
                LIMIT 1000
                """;
        List<RecommendationCandidate> candidates = jdbcTemplate.query(query, candidateMapper,
                userId, userId, userId, userId, userId, userId, userId, userId, userId, userId, userId,
                signalWeights.weight(InteractionType.SAVE), signalWeights.weight(InteractionType.PURCHASE),
                signalWeights.weight(InteractionType.LIKE), signalWeights.weight(InteractionType.SHARE),
                userId, userId, userId, userId);
        if (candidates.isEmpty()) return candidates;
        Map<Long, SignalAggregate> collaborative = findCollaborativeSignals(userId);
        Map<Long, SignalAggregate> popularity = findCatalogPopularity(userId);
        Map<Long, ContentFeatureScorer.Match> content = findContentMatches(userId, candidates);
        long maxPopularity = popularity.values().stream().mapToLong(SignalAggregate::eventCount).max().orElse(0);
        return candidates.stream().map(candidate -> {
            SignalAggregate collab = collaborative.getOrDefault(candidate.itemId(), SignalAggregate.EMPTY);
            SignalAggregate popular = popularity.getOrDefault(candidate.itemId(), SignalAggregate.EMPTY);
            ContentFeatureScorer.Match contentMatch = content.get(candidate.itemId());
            BigDecimal popularityScore = maxPopularity == 0 || popular.eventCount() == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(Math.log1p(popular.eventCount()) / Math.log1p(maxPopularity));
            return new RecommendationCandidate(candidate.itemId(), candidate.itemTitle(),
                    candidate.categoryPreferenceWeight(), candidate.interestMatch(),
                    candidate.userAverageRating(), candidate.itemAverageRating(),
                    candidate.positiveInteractionSignal(), candidate.negativeInteractionSignal(),
                    candidate.qualityScore(), candidate.categoryName(), candidate.matchedTopics(),
                    candidate.relatedInterestMatch(), candidate.purposeMatch(), collab.score(),
                    collab.evidenceCount(), popularityScore, popular.evidenceCount(),
                    contentMatch == null ? BigDecimal.ZERO : BigDecimal.valueOf(contentMatch.score()),
                    contentMatch == null ? "" : contentMatch.overlappingTerms(), candidate.editorPick());
        }).toList();
    }

    private Map<Long, ContentFeatureScorer.Match> findContentMatches(long userId,
                                                                     List<RecommendationCandidate> candidates) {
        List<Long> seedIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT event.item_id
                FROM interactions event
                JOIN items item ON item.item_id = event.item_id AND item.is_active = TRUE
                WHERE event.user_id = ?
                  AND event.occurred_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 180 DAY)
                  AND event.interaction_type IN ('LIKE', 'SAVE', 'PURCHASE', 'SHARE', 'MORE_LIKE')
                  AND NOT EXISTS (
                      SELECT 1 FROM interactions rejection
                      WHERE rejection.user_id = event.user_id AND rejection.item_id = event.item_id
                        AND rejection.interaction_type IN ('DISLIKE', 'HIDE')
                  )
                """, Long.class, userId);
        java.util.Set<Long> documentIds = new java.util.HashSet<>(seedIds);
        candidates.forEach(candidate -> documentIds.add(candidate.itemId()));
        List<Long> boundedIds = documentIds.stream().sorted().toList();
        if (boundedIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(boundedIds.size(), "?"));
        List<ContentFeatureScorer.ItemDocument> documents = jdbcTemplate.query("""
                SELECT item.item_id, CONCAT_WS(' ', item.title, item.description, category.name,
                       GROUP_CONCAT(DISTINCT interest.name SEPARATOR ' ')) AS feature_text
                FROM items item
                JOIN categories category ON category.category_id = item.category_id
                LEFT JOIN item_interests item_interest ON item_interest.item_id = item.item_id
                LEFT JOIN interests interest ON interest.interest_id = item_interest.interest_id
                    AND interest.is_active = TRUE
                WHERE item.item_id IN (%s) AND item.is_active = TRUE AND category.is_active = TRUE
                GROUP BY item.item_id, item.title, item.description, category.name
                """.formatted(placeholders), (resultSet, row) -> new ContentFeatureScorer.ItemDocument(
                resultSet.getLong("item_id"), resultSet.getString("feature_text")), boundedIds.toArray());
        return contentFeatureScorer.score(documents, new java.util.HashSet<>(seedIds));
    }

    /** Implicit user-neighborhood score; only aggregates, never another user's identity, reach the ranker. */
    private Map<Long, SignalAggregate> findCollaborativeSignals(long userId) {
        String query = """
                WITH positive_user_items AS (
                    SELECT event.user_id, event.item_id, LEAST(COUNT(*), 3) AS strength
                    FROM interactions event
                    WHERE event.occurred_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 180 DAY)
                      AND event.interaction_type IN ('LIKE', 'SAVE', 'PURCHASE', 'SHARE', 'MORE_LIKE')
                      AND NOT EXISTS (
                          SELECT 1 FROM interactions rejection
                          WHERE rejection.user_id = event.user_id AND rejection.item_id = event.item_id
                            AND rejection.interaction_type IN ('DISLIKE', 'HIDE')
                      )
                    GROUP BY event.user_id, event.item_id
                ),
                target_items AS (
                    SELECT item_id FROM positive_user_items WHERE user_id = ?
                ),
                target_count AS (SELECT COUNT(*) AS item_count FROM target_items),
                user_item_counts AS (
                    SELECT user_id, COUNT(*) AS item_count FROM positive_user_items GROUP BY user_id
                ),
                similar_users AS (
                    SELECT other.user_id,
                           COUNT(DISTINCT target.item_id) / SQRT(GREATEST(1,
                               MAX(target_count.item_count) * MAX(other_count.item_count))) AS similarity,
                           COUNT(DISTINCT target.item_id) AS overlap_count
                    FROM target_items target
                    JOIN positive_user_items other ON other.item_id = target.item_id
                    JOIN user_item_counts other_count ON other_count.user_id = other.user_id
                    CROSS JOIN target_count
                    WHERE other.user_id <> ?
                    GROUP BY other.user_id
                    HAVING overlap_count >= 2
                )
                SELECT next_item.item_id,
                       COUNT(DISTINCT similar.user_id) AS evidence_count,
                       LEAST(1.0,
                           SUM(similar.similarity * LEAST(next_item.strength / 2.0, 1.0))
                               / NULLIF(SUM(similar.similarity), 0)
                           * COUNT(DISTINCT similar.user_id)
                               / (COUNT(DISTINCT similar.user_id) + 2.0)) AS match_score
                FROM similar_users similar
                JOIN positive_user_items next_item ON next_item.user_id = similar.user_id
                LEFT JOIN target_items already_seen ON already_seen.item_id = next_item.item_id
                WHERE already_seen.item_id IS NULL
                GROUP BY next_item.item_id
                HAVING evidence_count >= 3
                """;
        return jdbcTemplate.query(query, (resultSet, row) -> Map.entry(resultSet.getLong("item_id"),
                        new SignalAggregate(resultSet.getBigDecimal("match_score"),
                                resultSet.getInt("evidence_count"), 0L)), userId, userId)
                .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Recent positive activity by other users is aggregated without exposing user-level histories. */
    private Map<Long, SignalAggregate> findCatalogPopularity(long userId) {
        String query = """
                SELECT item_id, COUNT(DISTINCT user_id) AS event_count,
                       COUNT(DISTINCT user_id) AS evidence_count
                FROM interactions
                WHERE user_id <> ?
                  AND occurred_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 DAY)
                  AND interaction_type IN ('LIKE', 'SAVE', 'PURCHASE', 'SHARE', 'MORE_LIKE')
                GROUP BY item_id
                """;
        return jdbcTemplate.query(query, (resultSet, row) -> Map.entry(resultSet.getLong("item_id"),
                        new SignalAggregate(BigDecimal.ZERO, resultSet.getInt("evidence_count"),
                                resultSet.getLong("event_count"))), userId)
                .stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public long createBatch(long userId, String algorithmVersion) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO recommendation_batches (user_id, algorithm_version) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, algorithmVersion);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) throw new IllegalStateException("The database did not return the new recommendation batch ID.");
        return generatedId.longValue();
    }

    @Override
    public PersonalizedRecommendation save(long userId, long batchId, RecommendationDraft recommendation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO recommendations
                        (batch_id, user_id, item_id, score, reason, reason_type, bucket_type, algorithm_version,
                         explanation_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, batchId);
            statement.setLong(2, userId);
            statement.setLong(3, recommendation.itemId());
            statement.setBigDecimal(4, recommendation.score());
            statement.setString(5, recommendation.reason());
            statement.setString(6, recommendation.reasonType());
            statement.setString(7, recommendation.bucketType());
            statement.setString(8, "hybrid-evidence-v2");
            statement.setString(9, writeBreakdown(recommendation.scoreBreakdown()));
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) throw new IllegalStateException("The database did not return the new recommendation ID.");
        return jdbcTemplate.query("""
                SELECT recommendation.recommendation_id, recommendation.batch_id, recommendation.item_id, item.title,
                       recommendation.score, recommendation.reason, recommendation.reason_type,
                       recommendation.bucket_type, recommendation.generated_at, recommendation.explanation_json
                FROM recommendations recommendation JOIN items item ON item.item_id = recommendation.item_id
                WHERE recommendation.recommendation_id = ?
                """, recommendationMapper, generatedId.longValue()).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("The recommendation could not be read back."));
    }

    @Override
    public List<PersonalizedRecommendation> findLatestForUser(long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT recommendation.recommendation_id, recommendation.batch_id, recommendation.item_id, item.title,
                       recommendation.score, recommendation.reason, recommendation.reason_type,
                       recommendation.bucket_type, recommendation.generated_at, recommendation.explanation_json
                FROM recommendations recommendation JOIN items item ON item.item_id = recommendation.item_id
                WHERE recommendation.user_id = ? AND recommendation.batch_id <=> (
                    SELECT batch.batch_id FROM recommendation_batches batch WHERE batch.user_id = ?
                    ORDER BY batch.generated_at DESC, batch.batch_id DESC LIMIT 1
                )
                ORDER BY recommendation.generated_at DESC, recommendation.recommendation_id DESC LIMIT ?
                """, recommendationMapper, userId, userId, limit);
    }

    @Override
    public int deleteAllForUser(long userId) {
        int deleted = jdbcTemplate.update("DELETE FROM recommendations WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM recommendation_batches WHERE user_id = ?", userId);
        return deleted;
    }

    private String writeBreakdown(Map<String, BigDecimal> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) return "{}";
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> entry : breakdown.entrySet()) {
            if (!first) json.append(',');
            json.append('"').append(entry.getKey()).append("\":").append(entry.getValue().toPlainString());
            first = false;
        }
        return json.append('}').toString();
    }

    private Map<String, BigDecimal> readBreakdown(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return Map.of();
        Matcher matcher = Pattern.compile("\\\"([a-z_]+)\\\"\\s*:\\s*(-?(?:0|[1-9]\\d*)(?:\\.\\d+)?)")
                .matcher(json);
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        while (matcher.find()) values.put(matcher.group(1), new BigDecimal(matcher.group(2)));
        if (values.isEmpty()) throw new IllegalStateException("Stored recommendation explanation is invalid.");
        return Map.copyOf(values);
    }

    private record SignalAggregate(BigDecimal score, int evidenceCount, long eventCount) {
        private static final SignalAggregate EMPTY = new SignalAggregate(BigDecimal.ZERO, 0, 0);
    }
}
