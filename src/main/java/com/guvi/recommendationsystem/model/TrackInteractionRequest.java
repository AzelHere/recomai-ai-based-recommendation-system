package com.guvi.recommendationsystem.model;

public record TrackInteractionRequest(long itemId, InteractionType type) {
}
