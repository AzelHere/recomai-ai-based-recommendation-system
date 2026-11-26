package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Interest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcInterestDao implements InterestDao {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Interest> mapper = (resultSet, rowNumber) -> new Interest(
            resultSet.getLong("interest_id"), resultSet.getString("name"),
            resultSet.getBoolean("is_active"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime());

    public JdbcInterestDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Interest> findById(long interestId) {
        return jdbcTemplate.query("""
                SELECT interest_id, name, is_active, created_at, updated_at
                FROM interests WHERE interest_id = ?
                """, mapper, interestId).stream().findFirst();
    }

    @Override
    public List<Interest> findActive() {
        return jdbcTemplate.query("""
                SELECT interest_id, name, is_active, created_at, updated_at
                FROM interests WHERE is_active = TRUE ORDER BY name, interest_id
                """, mapper);
    }

    @Override
    public List<Interest> findAll() {
        return jdbcTemplate.query("""
                SELECT interest_id, name, is_active, created_at, updated_at
                FROM interests ORDER BY name, interest_id
                """, mapper);
    }

    @Override
    public boolean existsById(long interestId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM interests WHERE interest_id = ?)",
                Boolean.class, interestId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Interest insert(String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            return com.guvi.recommendationsystem.dao.JdbcStatements.generated(connection,
                    "INSERT INTO interests (name) VALUES (?)", statement -> {
            statement.setString(1, name);
            });
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) {
            throw new IllegalStateException("The database did not return the new interest ID.");
        }
        return findById(id.longValue())
                .orElseThrow(() -> new IllegalStateException("The new interest could not be read back."));
    }

    @Override
    public boolean update(long interestId, String name) {
        return jdbcTemplate.update(
                "UPDATE interests SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE interest_id = ?",
                name, interestId) == 1;
    }

    @Override
    public boolean setActive(long interestId, boolean active) {
        return jdbcTemplate.update(
                "UPDATE interests SET is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE interest_id = ?",
                active, interestId) == 1;
    }
}
