package com.guvi.recommendationsystem.exception;

public class InvalidUserInputException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidUserInputException(String message) {
        super(message);
    }
}
