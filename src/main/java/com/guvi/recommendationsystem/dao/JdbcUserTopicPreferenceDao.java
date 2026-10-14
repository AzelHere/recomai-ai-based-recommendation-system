package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.InterestInsight;
import com.guvi.recommendationsystem.model.InterestProfile;
import com.guvi.recommendationsystem.model.InteractionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Repository
public class JdbcUserTopicPreferenceDao implements UserTopicPreferenceDao {
    private final JdbcTemplate jdbcTemplate;
    private final com.guvi.recommendationsystem.service.TopicPreferenceScorer scorer;

    public JdbcUserTopicPreferenceDao(JdbcTemplate jdbcTemplate,
            com.guvi.recommendationsystem.service.TopicPreferenceScorer scorer) {
        this.jdbcTemplate = jdbcTemplate;
        this.scorer = scorer;
    }

    @Override
    @Transactional
    public void applyInteraction(long userId, long itemId, InteractionType type, double signalWeight, int halfLifeDays) {
        List<Long> interestIds = jdbcTemplate.queryForList(
                "SELECT interest_id FROM item_interests WHERE item_id = ? ORDER BY interest_id",
                Long.class, itemId);
        LocalDateTime now = LocalDateTime.now();
        for (Long interestId : interestIds) {
            applyTopicSignal(userId, interestId, signalWeight, halfLifeDays, now);
        }
    }

    @Override
    @Transactional
    public void applySearch(long userId, String query, double signalWeight, int halfLifeDays) {
        List<String> terms = SearchTerms.tokenize(query);
        if (terms.isEmpty()) return;
        StringBuilder match = new StringBuilder();
        List<Object> arguments = new ArrayList<>();
        for (String ignored : terms) {
            if (!match.isEmpty()) match.append(" OR ");
            match.append("LOCATE(?, LOWER(item.title)) > 0 OR "
                    + "LOCATE(?, LOWER(COALESCE(item.description, ''))) > 0 OR "
                    + "LOCATE(?, LOWER(interest.name)) > 0");
        }
        terms.forEach(term -> {
            arguments.add(term);
            arguments.add(term);
            arguments.add(term);
        });
        List<Long> interestIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT tagged.interest_id
                FROM item_interests tagged
                JOIN items item ON item.item_id = tagged.item_id AND item.is_active = TRUE
                JOIN interests interest ON interest.interest_id = tagged.interest_id AND interest.is_active = TRUE
                """ + "WHERE " + match + " ORDER BY tagged.interest_id", Long.class, arguments.toArray());
        LocalDateTime now = LocalDateTime.now();
        for (Long interestId : interestIds) {
            applyTopicSignal(userId, interestId, signalWeight, halfLifeDays, now);
        }
    }

    private void applyTopicSignal(long userId, long interestId, double signalWeight, int halfLifeDays,
                                  LocalDateTime now) {
        TopicState previous = jdbcTemplate.query("""
                SELECT positive_score, negative_score, preference_score, interaction_count, last_interacted_at
                FROM user_topic_preferences WHERE user_id = ? AND interest_id = ?
                """, resultSet -> resultSet.next()
                ? new TopicState(resultSet.getBigDecimal("positive_score").doubleValue(),
                        resultSet.getBigDecimal("negative_score").doubleValue(),
                        resultSet.getBigDecimal("preference_score").doubleValue(),
                        resultSet.getLong("interaction_count"),
                        resultSet.getTimestamp("last_interacted_at") == null ? null
                                : resultSet.getTimestamp("last_interacted_at").toLocalDateTime())
                : new TopicState(0, 0, 0, 0, null), userId, interestId);

        var signal = scorer.apply(previous.positiveScore(), previous.negativeScore(), previous.preferenceScore(),
                previous.interactionCount(), previous.lastInteractedAt(), signalWeight, now, halfLifeDays);

        jdbcTemplate.update("""
                INSERT INTO user_topic_preferences
                    (user_id, interest_id, positive_score, negative_score, preference_score,
                     interaction_count, confidence, trend, last_interacted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE positive_score = VALUES(positive_score),
                    negative_score = VALUES(negative_score), preference_score = VALUES(preference_score),
                    interaction_count = VALUES(interaction_count), confidence = VALUES(confidence),
                    trend = VALUES(trend), last_interacted_at = VALUES(last_interacted_at)
                """, userId, interestId, BigDecimal.valueOf(signal.positiveScore()),
                BigDecimal.valueOf(signal.negativeScore()), BigDecimal.valueOf(signal.preferenceScore()),
                signal.interactionCount(), BigDecimal.valueOf(signal.confidence()), signal.trend(), Timestamp.valueOf(now));
    }

    @Override
    public InterestProfile findProfile(long userId) {
        List<InterestInsight> current = jdbcTemplate.query("""
                SELECT interest.interest_id, interest.name AS topic_name, category.name AS niche_name,
                       COALESCE(topic.preference_score * POW(0.5, GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR,
                           topic.last_interacted_at, CURRENT_TIMESTAMP), 0)) / (24.0 * 45)), 0) AS preference_score,
                       COALESCE(topic.confidence * POW(0.5, GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR,
                           topic.last_interacted_at, CURRENT_TIMESTAMP), 0)) / (24.0 * 45)), 0) AS confidence,
                       COALESCE(topic.interaction_count, 0) AS interaction_count,
                       COALESCE(topic.trend, 'STABLE') AS trend,
                       topic.interest_id IS NOT NULL AS has_behavior,
                       onboarding_topic.user_id IS NOT NULL AS from_onboarding,
                       COALESCE(selected.interest_weight, 0) AS selected_weight,
                       onboarding_topic.selected_at AS selected_at
                FROM interests interest
                JOIN categories category ON category.category_id = interest.category_id
                LEFT JOIN user_interests selected
                  ON selected.interest_id = interest.interest_id AND selected.user_id = ?
                LEFT JOIN user_onboarding_topics onboarding_topic
                  ON onboarding_topic.interest_id = interest.interest_id AND onboarding_topic.user_id = ?
                LEFT JOIN user_topic_preferences topic
                  ON topic.interest_id = interest.interest_id AND topic.user_id = ?
                WHERE interest.is_active = TRUE AND (selected.user_id IS NOT NULL OR topic.interest_id IS NOT NULL)
                """, (resultSet, row) -> {
            boolean behavioral = resultSet.getBoolean("has_behavior");
            double score = resultSet.getBigDecimal("preference_score").doubleValue();
            double selectedWeight = resultSet.getBigDecimal("selected_weight").doubleValue();
            double selectedDecay = resultSet.getTimestamp("selected_at") == null ? 1.0
                    : scorer.decay(1.0, resultSet.getTimestamp("selected_at").toLocalDateTime(), LocalDateTime.now(), 60);
            int strength = behavioral ? (int) Math.round(Math.max(0, Math.min(1, 0.5 + score / 2.0)) * 100)
                    : (int) Math.round((resultSet.getBoolean("from_onboarding") ? 0.7 : selectedWeight) * 100 * selectedDecay);
            long count = resultSet.getLong("interaction_count");
            double confidence = resultSet.getBigDecimal("confidence").doubleValue();
            String confidenceLabel = !behavioral || confidence < 0.35 ? "early signals"
                    : confidence < 0.70 ? "building confidence" : "consistent signals";
            String evidence = behavioral
                    ? "Inferred from " + count + " interactions in this topic."
                    : "You selected this topic; activity signals have not built yet.";
            return new InterestInsight(resultSet.getLong("interest_id"), resultSet.getString("topic_name"),
                    resultSet.getString("niche_name"), strength, resultSet.getString("trend"),
                    confidenceLabel, evidence);
                }, userId, userId, userId);

        current.addAll(jdbcTemplate.query("""
                SELECT category.category_id, category.name, preference.preference_weight,
                       onboarding.completed_at
                FROM user_onboarding onboarding
                JOIN categories category ON category.category_id = onboarding.category_id
                JOIN user_preferences preference
                  ON preference.user_id = onboarding.user_id AND preference.category_id = onboarding.category_id
                WHERE onboarding.user_id = ?
                """, (resultSet, row) -> {
            double decay = scorer.decay(1.0, resultSet.getTimestamp("completed_at").toLocalDateTime(),
                    LocalDateTime.now(), 60);
            int strength = (int) Math.round(resultSet.getBigDecimal("preference_weight").doubleValue() * decay * 100);
            return new InterestInsight(0, resultSet.getString("name"), resultSet.getString("name"), strength,
                    "STABLE", "initial signal", "You chose this area during onboarding; behavior may change this estimate.");
        }, userId));

        List<InterestInsight> growing = current.stream()
                .filter(insight -> "UP".equals(insight.trend()) && insight.strengthPercent() >= 15
                        && !"early signals".equals(insight.confidence()))
                .sorted(Comparator.comparingInt(InterestInsight::strengthPercent).reversed()).limit(5).toList();
        List<InterestInsight> declining = current.stream()
                .filter(insight -> "DOWN".equals(insight.trend()))
                .sorted(Comparator.comparingInt(InterestInsight::strengthPercent)).limit(5).toList();
        List<InterestInsight> exploreNext = jdbcTemplate.query("""
                SELECT interest.interest_id, interest.name, category.name AS niche_name
                FROM interests interest
                JOIN categories category ON category.category_id = interest.category_id
                WHERE interest.is_active = TRUE
                  AND interest.interest_id NOT IN (
                      SELECT selected.interest_id FROM user_interests selected WHERE selected.user_id = ?
                      UNION
                      SELECT topic.interest_id FROM user_topic_preferences topic WHERE topic.user_id = ?
                  )
                ORDER BY category.niche_code, interest.name
                LIMIT 6
                """, (resultSet, row) -> new InterestInsight(
                        resultSet.getLong("interest_id"), resultSet.getString("name"),
                        resultSet.getString("niche_name"), 0, "STABLE", "not inferred",
                        "A topic available to explore; it is not an inferred preference."), userId, userId);

        List<InterestInsight> rankedCurrent = new ArrayList<>(current);
        rankedCurrent.sort(Comparator.comparingInt(InterestInsight::strengthPercent).reversed());
        return new InterestProfile(List.copyOf(rankedCurrent), growing, declining, exploreNext);
    }

    @Override
    public int deleteAllForUser(long userId) {
        return jdbcTemplate.update("DELETE FROM user_topic_preferences WHERE user_id = ?", userId);
    }

    private record TopicState(double positiveScore, double negativeScore, double preferenceScore,
                              long interactionCount, LocalDateTime lastInteractedAt) {
    }
}
