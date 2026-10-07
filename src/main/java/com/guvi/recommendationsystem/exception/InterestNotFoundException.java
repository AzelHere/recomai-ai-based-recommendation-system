package com.guvi.recommendationsystem.exception;

public class InterestNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InterestNotFoundException(String message) {
        super(message);
    }
}
