package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Optional;
import java.util.OptionalInt;

@Repository
public class JdbcUserDao implements UserDao {
    @Override public void lock(long userId) {
        jdbcTemplate.queryForObject("SELECT user_id FROM users WHERE user_id=? FOR UPDATE",Long.class,userId);
    }
    private static final String SELECT_COLUMNS =
            "user_id, username, email, password_hash, display_name, role, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserAccount> userMapper = (resultSet, rowNumber) -> new UserAccount(
            resultSet.getLong("user_id"),
            resultSet.getString("username"),
            resultSet.getString("email"),
            resultSet.getString("password_hash"),
            resultSet.getString("display_name"),
            resultSet.getString("role"),
            resultSet.getTimestamp("created_at").toLocalDateTime(),
            resultSet.getTimestamp("updated_at").toLocalDateTime());

    public JdbcUserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM users WHERE user_id = ?",
                userMapper,
                userId).stream().findFirst();
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM users WHERE email = ?",
                userMapper,
                email).stream().findFirst();
    }

    @Override
    public boolean existsById(long userId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE user_id = ?)",
                Boolean.class,
                userId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public OptionalInt findAuthVersion(long userId) {
        return jdbcTemplate.query("SELECT auth_version FROM users WHERE user_id = ?",
                (rs, row) -> rs.getInt(1), userId).stream().mapToInt(Integer::intValue).findFirst();
    }

    @Override
    public UserAccount insertUser(String username, String email, String passwordHash, String displayName) {
        String sql = """
                INSERT INTO users (username, email, password_hash, display_name, role)
                VALUES (?, ?, ?, ?, 'USER')
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, displayName);
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("The database did not return the new user ID.");
        }
        return findById(generatedId.longValue())
                .orElseThrow(() -> new IllegalStateException("The new user could not be read back."));
    }

    @Override
    public boolean updateDisplayName(long userId, String displayName) {
        return jdbcTemplate.update(
                "UPDATE users SET display_name = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                displayName,
                userId) == 1;
    }

    @Override
    public boolean deleteById(long userId) {
        return jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", userId) == 1;
    }
}
