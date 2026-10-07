package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserAccount;

import java.util.Optional;

public interface UserDao {
    Optional<UserAccount> findById(long userId);

    Optional<UserAccount> findByEmail(String email);

    boolean existsById(long userId);

    UserAccount insertUser(String username, String email, String passwordHash, String displayName);

    boolean updateDisplayName(long userId, String displayName);
}
