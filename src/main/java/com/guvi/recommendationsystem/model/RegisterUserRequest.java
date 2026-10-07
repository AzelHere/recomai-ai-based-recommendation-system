package com.guvi.recommendationsystem.model;

public record RegisterUserRequest(String username, String email, String password, String displayName) {
}
