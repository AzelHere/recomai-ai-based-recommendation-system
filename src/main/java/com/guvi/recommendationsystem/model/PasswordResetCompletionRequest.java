package com.guvi.recommendationsystem.model;

public record PasswordResetCompletionRequest(String token, String password, String confirmPassword) { }
