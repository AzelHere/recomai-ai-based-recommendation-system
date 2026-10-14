package com.guvi.recommendationsystem.model;

public record SearchInteractionRequest(String query, String requestId) {
    public SearchInteractionRequest(String query) {
        this(query, null);
    }
}
