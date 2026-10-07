package com.guvi.recommendationsystem.exception;

public class DuplicateCatalogEntryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DuplicateCatalogEntryException(String message) {
        super(message);
    }
}
