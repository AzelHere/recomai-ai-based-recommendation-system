package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.InteractionType;
import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.service.InteractionSignalWeights;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.math.BigDecimal;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class JdbcRecommendationDaoTest {
    @SuppressWarnings({"rawtypes","unchecked"})
    @Test void restoringAFeedPreservesGeneratedOrderAndRechecksEligibility() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
        doReturn(List.of()).when(jdbc).query(sql.capture(),any(RowMapper.class),eq(17L),eq(17L),eq(10));
        new JdbcRecommendationDao(jdbc,mock(InteractionSignalWeights.class)).findLatestForUser(17L,10);
        assertThat(sql.getValue()).contains("ORDER BY recommendation.recommendation_id ASC")
                .contains("recommendation.user_id = ?").contains("item.is_active=TRUE")
                .contains("category.is_active=TRUE").contains("rejection.user_id=recommendation.user_id")
                .contains("rejection.interaction_type IN ('HIDE','DISLIKE')");
    }
    @Test
    void impressionAndClickWritesAreRecommendationOwnerScopedAndIdempotent() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        new JdbcRecommendationDao(jdbcTemplate, mock(InteractionSignalWeights.class))
                .recordImpression(17L, 42L);
        new JdbcRecommendationDao(jdbcTemplate, mock(InteractionSignalWeights.class))
                .recordClick(17L, 42L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(sql.capture(), org.mockito.ArgumentMatchers.any(Object[].class));
        assertThat(sql.getAllValues().get(0)).contains("INSERT IGNORE INTO recommendation_impressions")
                .contains("FROM recommendations")
                .contains("WHERE recommendation_id = ? AND user_id = ?");
        assertThat(sql.getAllValues().get(1)).contains("INSERT INTO recommendation_impressions")
                .contains("WHERE recommendation_id = ? AND user_id = ?")
                .contains("ON DUPLICATE KEY UPDATE clicked_at = COALESCE(clicked_at, CURRENT_TIMESTAMP(6))");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void candidateScoringKeepsOnboardingSignalsTopicScopedAndUndiluted() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        InteractionSignalWeights signalWeights = mock(InteractionSignalWeights.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        doReturn(List.of()).when(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        when(signalWeights.weight(any(InteractionType.class))).thenReturn(0.5);

        new JdbcRecommendationDao(jdbcTemplate, signalWeights).findCandidates(17L);

        String query = sql.getValue();
        assertThat(query).contains("item.item_type IN ('COURSE', 'GUIDE', 'DOCUMENTATION', 'ARTICLE')").contains("user_topic_behavior_maturity AS")
                .contains("user_category_ratings AS")
                .contains("GROUP BY item.category_id")
                .contains("user_ratings.average_rating AS user_average_rating")
                .contains("SELECT interest_id, 1.0 - EXP(-interaction_count / 10.0) AS strength")
                .contains("FROM user_topic_preferences WHERE user_id = ?")
                .contains("topic_maturity.interest_id = interest.interest_id")
                .contains("topic_maturity.interest_id = relation.interest_id")
                .contains("CASE WHEN onboarding_topic.user_id IS NULL THEN 1.0")
                .contains("ELSE 1.0 - COALESCE(topic_maturity.strength, 0) END")
                .contains("LEAST(COALESCE(interest_match.match_weight, 0), 1.0)")
                .contains("LEAST(COALESCE(related_match.match_weight, 0), 1.0)")
                .contains("SELECT item_id, AVG(rating) AS average_rating FROM feedback")
                .contains("WHERE user_id <> ? GROUP BY item_id")
                .contains("GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR, occurred_at, CURRENT_TIMESTAMP), 0))")
                .contains("GREATEST(0, COALESCE(TIMESTAMPDIFF(HOUR, onboarding_topic.selected_at, CURRENT_TIMESTAMP), 0))")
                .doesNotContain("user_interest_totals")
                .doesNotContain("interest_match.match_weight / NULLIF")
                .doesNotContain("SUM(interaction_count) AS strength");
        String itemSignalSql = query.substring(query.indexOf("user_interaction_signals AS ("),
                query.indexOf("SELECT item.item_id"));
        assertThat(itemSignalSql).contains("WHEN 'SAVE' THEN ?", "WHEN 'LIKE' THEN ?", "WHEN 'SHARE' THEN ?")
                .doesNotContain("WHEN 'VIEW' THEN ?", "WHEN 'READ_PROGRESS' THEN ?", "MORE_LIKE", "DISLIKE", "SKIP");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void progressQueryUsesLatestRelevantEventPerItemAcrossTheFullUserHistory() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        doReturn(List.of()).when(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(17L));

        new JdbcInteractionDao(jdbcTemplate).findCurrentProgressByUser(17L);

        assertThat(sql.getValue()).contains("ROW_NUMBER() OVER (PARTITION BY item_id")
                .contains("interaction_type IN ('READ_PROGRESS', 'COMPLETE')")
                .contains("WHERE user_id = ?")
                .contains("WHERE event_rank = 1");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void popularityEvidenceCountsDistinctUsersRatherThanRepeatedEvents() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        InteractionSignalWeights signalWeights = mock(InteractionSignalWeights.class);
        List<String> statements = new java.util.ArrayList<>();
        RecommendationCandidate candidate = new RecommendationCandidate(42, "Editor pick",
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.5"), "Technology", "", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, "", true);

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            statements.add(sql);
            RowMapper mapper = invocation.getArgument(1);
            if (sql.contains("WITH user_category_ratings AS")) return List.of(candidate);
            if (sql.contains("COUNT(DISTINCT user_id) AS event_count")) {
                ResultSet row = mock(ResultSet.class);
                when(row.getLong("item_id")).thenReturn(42L);
                // Five activity rows from a single account still yield one user of evidence.
                when(row.getLong("event_count")).thenReturn(1L);
                when(row.getInt("evidence_count")).thenReturn(1);
                return List.of(mapper.mapRow(row, 0));
            }
            return List.of();
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), any(Object[].class));
        doReturn(List.of()).when(jdbcTemplate).queryForList(anyString(), eq(Long.class), eq(17L));

        List<RecommendationCandidate> results = new JdbcRecommendationDao(jdbcTemplate, signalWeights)
                .findCandidates(17L);

        String popularityQuery = statements.stream()
                .filter(sql -> sql.contains("COUNT(DISTINCT user_id) AS event_count"))
                .findFirst().orElseThrow();
        assertThat(popularityQuery).contains("COUNT(DISTINCT user_id)")
                .contains("GROUP BY item_id")
                .contains("user_id <> ?");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().popularityEvidenceCount()).isEqualTo(1);
        assertThat(results.getFirst().popularityMatch()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
