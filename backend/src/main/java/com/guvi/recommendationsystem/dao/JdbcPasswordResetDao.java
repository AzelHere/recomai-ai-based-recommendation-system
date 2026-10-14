package com.guvi.recommendationsystem.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.OptionalLong;

@Repository
public class JdbcPasswordResetDao implements PasswordResetDao {
    private final JdbcTemplate jdbc;

    public JdbcPasswordResetDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public OptionalLong findUserIdByEmailForUpdate(String email) {
        List<Long> ids = jdbc.query("SELECT user_id FROM users WHERE email = ? FOR UPDATE",
                (rs, row) -> rs.getLong(1), email);
        return ids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ids.getFirst());
    }

    @Override
    public boolean hasRecentResetRequest(long userId, int seconds) {
        Boolean recent = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM password_reset_tokens "
                        + "WHERE user_id = ? AND created_at > UTC_TIMESTAMP(6) - INTERVAL ? SECOND)",
                Boolean.class, userId, seconds);
        return Boolean.TRUE.equals(recent);
    }

    @Override
    public void consumeOutstandingTokens(long userId) {
        jdbc.update("UPDATE password_reset_tokens SET consumed_at = UTC_TIMESTAMP(6) "
                + "WHERE user_id = ? AND consumed_at IS NULL", userId);
    }

    @Override
    public void insertToken(long userId, byte[] tokenHash) {
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) "
                        + "VALUES (?, ?, UTC_TIMESTAMP(6) + INTERVAL 15 MINUTE)", userId, tokenHash);
    }

    @Override
    public boolean consumeTokenAndUpdatePassword(byte[] tokenHash, String passwordHash) {
        List<Long> ids = jdbc.query("SELECT user_id FROM password_reset_tokens WHERE token_hash = ? "
                        + "AND consumed_at IS NULL AND expires_at > UTC_TIMESTAMP(6) FOR UPDATE",
                (rs, row) -> rs.getLong(1), tokenHash);
        if (ids.isEmpty()) return false;
        long userId = ids.getFirst();
        int consumed = jdbc.update("UPDATE password_reset_tokens SET consumed_at = UTC_TIMESTAMP(6) "
                + "WHERE token_hash = ? AND consumed_at IS NULL AND expires_at > UTC_TIMESTAMP(6)", tokenHash);
        if (consumed != 1) return false;
        int updated = jdbc.update("UPDATE users SET password_hash = ?, auth_version = auth_version + 1, "
                + "updated_at = UTC_TIMESTAMP() WHERE user_id = ?", passwordHash, userId);
        if (updated != 1) throw new IllegalStateException("Password reset account update failed.");
        invalidateOutstandingTokens(userId);
        return true;
    }

    @Override
    public void invalidateOutstandingTokens(long userId) {
        jdbc.update("UPDATE password_reset_tokens SET consumed_at = UTC_TIMESTAMP(6) "
                + "WHERE user_id = ? AND consumed_at IS NULL", userId);
    }

    @Override
    public void invalidateByHash(byte[] tokenHash) {
        jdbc.update("UPDATE password_reset_tokens SET consumed_at = UTC_TIMESTAMP(6) "
                + "WHERE token_hash = ? AND consumed_at IS NULL", tokenHash);
    }
}
