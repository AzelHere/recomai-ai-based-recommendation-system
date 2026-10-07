package com.guvi.recommendationsystem.service;

public interface PasswordHasher {
    String hash(String password);

    boolean matches(String password, String encodedPassword);
}
