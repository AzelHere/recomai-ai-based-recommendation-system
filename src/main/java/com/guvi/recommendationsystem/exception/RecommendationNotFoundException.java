package com.guvi.recommendationsystem.exception;

public class RecommendationNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RecommendationNotFoundException(String message) {
        super(message);
    }
}
