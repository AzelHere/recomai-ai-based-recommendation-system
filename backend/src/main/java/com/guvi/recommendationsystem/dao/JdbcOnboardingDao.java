package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.NicheOption;
import com.guvi.recommendationsystem.model.OnboardingPurpose;
import com.guvi.recommendationsystem.model.OnboardingStatus;
import com.guvi.recommendationsystem.model.TopicOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcOnboardingDao implements OnboardingDao {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOnboardingDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<NicheOption> findOptions() {
        Map<Long, NicheBuilder> niches = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT category.category_id, category.niche_code, category.name,
                       interest.interest_id, interest.name AS topic_name
                FROM categories category
                LEFT JOIN interests interest
                  ON interest.category_id = category.category_id AND interest.is_active = TRUE
                WHERE category.niche_code IS NOT NULL AND category.is_active = TRUE
                ORDER BY FIELD(category.niche_code, 'TECHNOLOGY', 'BUSINESS_FINANCE', 'LEARNING_CAREER',
                               'HEALTH_FITNESS', 'CREATIVE_LIFESTYLE'), interest.name
                """, resultSet -> {
            long id = resultSet.getLong("category_id");
            NicheBuilder builder = niches.get(id);
            if (builder == null) {
                builder = new NicheBuilder(id, resultSet.getString("niche_code"), resultSet.getString("name"));
                niches.put(id, builder);
            }
            long topicId = resultSet.getLong("interest_id");
            if (!resultSet.wasNull()) {
                builder.topics.add(new TopicOption(topicId, resultSet.getString("topic_name")));
            }
        });
        return niches.values().stream().map(NicheBuilder::build).toList();
    }

    @Override
    public Optional<OnboardingStatus> findStatus(long userId) {
        return jdbcTemplate.query("""
                SELECT purpose, category_id, completed_at FROM user_onboarding WHERE user_id = ?
                """, resultSet -> resultSet.next()
                ? Optional.of(new OnboardingStatus(true,
                        OnboardingPurpose.valueOf(resultSet.getString("purpose")),
                        resultSet.getLong("category_id"), resultSet.getTimestamp("completed_at").toLocalDateTime()))
                : Optional.empty(), userId);
    }

    @Override
    @Transactional
    public void save(long userId, OnboardingPurpose purpose, long categoryId, List<Long> interestIds) {
        List<Long> oldCategories = jdbcTemplate.queryForList(
                "SELECT category_id FROM user_onboarding WHERE user_id = ?", Long.class, userId);
        for (Long oldCategory : oldCategories) {
            if (oldCategory != categoryId) {
                jdbcTemplate.update("DELETE FROM user_preferences WHERE user_id = ? AND category_id = ? AND preference_source = 'ONBOARDING'",
                        userId, oldCategory);
            }
        }
        List<Long> previousTopics = jdbcTemplate.queryForList(
                "SELECT interest_id FROM user_onboarding_topics WHERE user_id = ?", Long.class, userId);
        for (Long previousTopic : previousTopics) {
            if (!interestIds.contains(previousTopic)) {
                jdbcTemplate.update("DELETE FROM user_interests WHERE user_id = ? AND interest_id = ?",
                        userId, previousTopic);
            }
        }
        jdbcTemplate.update("""
                INSERT INTO user_onboarding (user_id, purpose, category_id)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE purpose = VALUES(purpose), category_id = VALUES(category_id),
                                        completed_at = CURRENT_TIMESTAMP
                """, userId, purpose.name(), categoryId);
        jdbcTemplate.update("""
                INSERT INTO user_preferences (user_id, category_id, preference_weight, preference_source)
                VALUES (?, ?, 0.8500, 'ONBOARDING')
                ON DUPLICATE KEY UPDATE
                    preference_weight = IF(preference_source = 'ONBOARDING', GREATEST(preference_weight, 0.8500), preference_weight)
                """, userId, categoryId);
        jdbcTemplate.update("DELETE FROM user_onboarding_topics WHERE user_id = ?", userId);
        jdbcTemplate.batchUpdate("""
                    INSERT INTO user_interests (user_id, interest_id, interest_weight)
                    VALUES (?, ?, 0.7000)
                    ON DUPLICATE KEY UPDATE interest_weight = GREATEST(interest_weight, 0.7000)
                    """, interestIds, interestIds.size(), (statement, interestId) -> {
                statement.setLong(1, userId);
                statement.setLong(2, interestId);
            });
        jdbcTemplate.batchUpdate("""
                    INSERT INTO user_onboarding_topics (user_id, interest_id) VALUES (?, ?)
                    """, interestIds, interestIds.size(), (statement, interestId) -> {
                statement.setLong(1, userId);
                statement.setLong(2, interestId);
            });
    }

    @Override
    public boolean isSupportedNiche(long categoryId) {
        Boolean supported = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM categories WHERE category_id = ? AND niche_code IS NOT NULL AND is_active = TRUE)",
                Boolean.class, categoryId);
        return Boolean.TRUE.equals(supported);
    }

    @Override
    public boolean areTopicsInNiche(long categoryId, List<Long> interestIds) {
        if (interestIds.isEmpty()) {
            return true;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(interestIds.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(categoryId);
        arguments.addAll(interestIds);
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT interest_id) FROM interests
                WHERE category_id = ? AND is_active = TRUE AND interest_id IN (%s)
                """.formatted(placeholders), Integer.class, arguments.toArray());
        return count != null && count == interestIds.size();
    }

    private static final class NicheBuilder {
        private final long id;
        private final String code;
        private final String name;
        private final List<TopicOption> topics = new ArrayList<>();

        private NicheBuilder(long id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        private NicheOption build() {
            return new NicheOption(id, code, name, List.copyOf(topics));
        }
    }
}
