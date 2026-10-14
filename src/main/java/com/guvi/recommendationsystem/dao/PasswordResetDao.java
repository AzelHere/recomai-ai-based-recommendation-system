package com.guvi.recommendationsystem.dao;

import java.util.OptionalLong;

public interface PasswordResetDao {
    OptionalLong findUserIdByEmailForUpdate(String email);
    boolean hasRecentResetRequest(long userId, int seconds);
    void consumeOutstandingTokens(long userId);
    void insertToken(long userId, byte[] tokenHash);
    boolean consumeTokenAndUpdatePassword(byte[] tokenHash, String passwordHash);
    void invalidateOutstandingTokens(long userId);
    void invalidateByHash(byte[] tokenHash);
}
