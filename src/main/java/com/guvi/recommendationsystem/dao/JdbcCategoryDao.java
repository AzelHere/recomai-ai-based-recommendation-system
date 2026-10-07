package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Category;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCategoryDao implements CategoryDao {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Category> categoryMapper = (resultSet, rowNumber) -> new Category(
            resultSet.getLong("category_id"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            resultSet.getBoolean("is_active"),
            resultSet.getTimestamp("created_at").toLocalDateTime());

    public JdbcCategoryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Category> findById(long categoryId) {
        return jdbcTemplate.query("""
                SELECT category_id, name, description, is_active, created_at
                FROM categories
                WHERE category_id = ?
                """, categoryMapper, categoryId).stream().findFirst();
    }

    @Override
    public List<Category> findAll() {
        return jdbcTemplate.query("""
                SELECT category_id, name, description, is_active, created_at
                FROM categories
                ORDER BY name
                """, categoryMapper);
    }

    @Override
    public List<Category> findActive() {
        return jdbcTemplate.query("""
                SELECT category_id, name, description, is_active, created_at
                FROM categories
                WHERE is_active = TRUE
                ORDER BY name
                """, categoryMapper);
    }

    @Override
    public boolean existsById(long categoryId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM categories WHERE category_id = ?)",
                Boolean.class,
                categoryId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public boolean hasActiveItems(long categoryId) {
        Boolean hasItems = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM items WHERE category_id = ? AND is_active = TRUE)",
                Boolean.class,
                categoryId);
        return Boolean.TRUE.equals(hasItems);
    }

    @Override
    public Category insert(String name, String description) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO categories (name, description) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, description);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new category ID.");
        }
        return findById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException("The new category could not be read back."));
    }

    @Override
    public boolean update(long categoryId, String name, String description) {
        return jdbcTemplate.update(
                "UPDATE categories SET name = ?, description = ? WHERE category_id = ?",
                name,
                description,
                categoryId) == 1;
    }

    @Override
    public boolean setActive(long categoryId, boolean active) {
        return jdbcTemplate.update(
                "UPDATE categories SET is_active = ? WHERE category_id = ?",
                active,
                categoryId) == 1;
    }
}
