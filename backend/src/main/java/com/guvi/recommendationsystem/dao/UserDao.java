package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserAccount;

import java.util.Optional;
import java.util.OptionalInt;

public interface UserDao {
    void lock(long userId);
    Optional<UserAccount> findById(long userId);

    Optional<UserAccount> findByEmail(String email);

    boolean existsById(long userId);

    OptionalInt findAuthVersion(long userId);

    UserAccount insertUser(String username, String email, String passwordHash, String displayName);

    boolean updateDisplayName(long userId, String displayName);

    boolean deleteById(long userId);
}
