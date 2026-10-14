package com.guvi.recommendationsystem.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserSignalSummaryDao implements UserSignalSummaryDao {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserSignalSummaryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long countInteractions(long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interactions WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    @Override
    public long countFeedback(long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedback WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }
}
