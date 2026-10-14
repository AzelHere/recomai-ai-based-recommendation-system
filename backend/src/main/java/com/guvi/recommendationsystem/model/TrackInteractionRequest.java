package com.guvi.recommendationsystem.model;

/** requestId makes retries of one client action idempotent; new actions use a new ID. */
public record TrackInteractionRequest(long itemId, InteractionType type, String requestId) {
    public TrackInteractionRequest(long itemId, InteractionType type) {
        this(itemId, type, null);
    }
}
