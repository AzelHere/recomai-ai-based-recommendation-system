package com.guvi.recommendationsystem.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class JdbcItemInterestDao implements ItemInterestDao {
    private final JdbcTemplate jdbcTemplate;

    public JdbcItemInterestDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(long itemId, long interestId, BigDecimal weight) {
        jdbcTemplate.update("""
                INSERT INTO item_interests (item_id, interest_id, relevance_weight)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE relevance_weight = ?
                """, itemId, interestId, weight, weight);
    }

    @Override
    public boolean delete(long itemId, long interestId) {
        return jdbcTemplate.update(
                "DELETE FROM item_interests WHERE item_id = ? AND interest_id = ?",
                itemId, interestId) == 1;
    }
}
