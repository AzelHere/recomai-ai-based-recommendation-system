package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.AdminDashboardSummary;
import com.guvi.recommendationsystem.model.AdminRecommendationInsights;
import com.guvi.recommendationsystem.model.CategoryInsight;
import com.guvi.recommendationsystem.model.ItemInsight;
import com.guvi.recommendationsystem.model.RecommendationEngagementMetrics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class JdbcAdminAnalyticsDao implements AdminAnalyticsDao {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAdminAnalyticsDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AdminDashboardSummary getSummary() {
        return jdbcTemplate.queryForObject("""
                SELECT
                    (SELECT COUNT(*) FROM users) AS total_users,
                    (SELECT COUNT(DISTINCT user_id) FROM interactions
                     WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL 30 DAY) AS active_users,
                    (SELECT COUNT(*) FROM items) AS total_items,
                    (SELECT COUNT(*) FROM categories) AS total_categories,
                    (SELECT COUNT(*) FROM interactions) AS total_interactions,
                    (SELECT COUNT(*) FROM recommendations) AS recommendations_generated,
                    (SELECT COALESCE(AVG(rating), 0) FROM feedback) AS average_rating
                """, (resultSet, rowNumber) -> new AdminDashboardSummary(
                resultSet.getLong("total_users"),
                resultSet.getLong("active_users"),
                resultSet.getLong("total_items"),
                resultSet.getLong("total_categories"),
                resultSet.getLong("total_interactions"),
                resultSet.getLong("recommendations_generated"),
                resultSet.getBigDecimal("average_rating")));
    }

    @Override
    public AdminRecommendationInsights getRecommendationInsights(int limit) {
        BigDecimal averageScore = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(score), 0) FROM recommendations", BigDecimal.class);
        return new AdminRecommendationInsights(
                findRecommendationCounts(limit),
                findInteractionCounts(null, limit),
                findInteractionCounts("SAVE", limit),
                findInteractionCounts("DISLIKE", limit),
                findTopCategories(limit),
                averageScore == null ? BigDecimal.ZERO : averageScore);
    }

    @Override
    public RecommendationEngagementMetrics getRecommendationEngagement() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS impressions,
                       COUNT(clicked_at) AS clicks,
                       COALESCE(ROUND(COUNT(clicked_at) * 100.0 / NULLIF(COUNT(*), 0), 2), 0.00)
                           AS click_through_rate_percent
                FROM recommendation_impressions
                """, (resultSet, rowNumber) -> {
            long impressions = resultSet.getLong("impressions");
            return new RecommendationEngagementMetrics(impressions, resultSet.getLong("clicks"),
                    resultSet.getBigDecimal("click_through_rate_percent"), impressions > 0);
        });
    }

    private List<ItemInsight> findRecommendationCounts(int limit) {
        return jdbcTemplate.query("""
                SELECT item.item_id, item.title, category.name AS category_name,
                       COUNT(recommendation.recommendation_id) AS activity_count
                FROM recommendations recommendation
                JOIN items item ON item.item_id = recommendation.item_id
                JOIN categories category ON category.category_id = item.category_id
                GROUP BY item.item_id, item.title, category.name
                ORDER BY activity_count DESC, item.item_id
                LIMIT ?
                """, this::mapItemInsight, limit);
    }

    private List<ItemInsight> findInteractionCounts(String type, int limit) {
        String sql = """
                SELECT item.item_id, item.title, category.name AS category_name,
                       COUNT(interaction.interaction_id) AS activity_count
                FROM interactions interaction
                JOIN items item ON item.item_id = interaction.item_id
                JOIN categories category ON category.category_id = item.category_id
                """;
        if (type != null) {
            sql += " WHERE interaction.interaction_type = ?";
        }
        sql += " GROUP BY item.item_id, item.title, category.name"
                + " ORDER BY activity_count DESC, item.item_id LIMIT ?";
        if (type == null) {
            return jdbcTemplate.query(sql, this::mapItemInsight, limit);
        }
        return jdbcTemplate.query(sql, this::mapItemInsight, type, limit);
    }

    private List<CategoryInsight> findTopCategories(int limit) {
        return jdbcTemplate.query("""
                SELECT category.category_id, category.name,
                       COUNT(recommendation.recommendation_id) AS recommendation_count
                FROM recommendations recommendation
                JOIN items item ON item.item_id = recommendation.item_id
                JOIN categories category ON category.category_id = item.category_id
                GROUP BY category.category_id, category.name
                ORDER BY recommendation_count DESC, category.category_id
                LIMIT ?
                """, (resultSet, rowNumber) -> new CategoryInsight(
                resultSet.getLong("category_id"), resultSet.getString("name"),
                resultSet.getLong("recommendation_count")), limit);
    }

    private ItemInsight mapItemInsight(java.sql.ResultSet resultSet, int rowNumber)
            throws java.sql.SQLException {
        return new ItemInsight(resultSet.getLong("item_id"), resultSet.getString("title"),
                resultSet.getString("category_name"), resultSet.getLong("activity_count"));
    }
}
