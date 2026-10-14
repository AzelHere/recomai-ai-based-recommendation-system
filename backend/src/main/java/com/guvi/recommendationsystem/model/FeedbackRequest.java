package com.guvi.recommendationsystem.model;

public record FeedbackRequest(Long itemId, Integer rating, String comment) {
}
