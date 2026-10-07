package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserInterest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class JdbcUserInterestDao implements UserInterestDao {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserInterestDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UserInterest> findByUserId(long userId) {
        return jdbcTemplate.query("""
                SELECT interest.interest_id, interest.name, user_interest.interest_weight
                FROM user_interests user_interest
                JOIN interests interest ON interest.interest_id = user_interest.interest_id
                WHERE user_interest.user_id = ? AND interest.is_active = TRUE
                ORDER BY interest.name, interest.interest_id
                """, (resultSet, rowNumber) -> new UserInterest(
                resultSet.getLong("interest_id"), resultSet.getString("name"),
                resultSet.getBigDecimal("interest_weight")), userId);
    }

    @Override
    public void save(long userId, long interestId, BigDecimal weight) {
        jdbcTemplate.update("""
                INSERT INTO user_interests (user_id, interest_id, interest_weight)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE interest_weight = ?
                """, userId, interestId, weight, weight);
    }

    @Override
    public boolean delete(long userId, long interestId) {
        return jdbcTemplate.update(
                "DELETE FROM user_interests WHERE user_id = ? AND interest_id = ?",
                userId, interestId) == 1;
    }
}
