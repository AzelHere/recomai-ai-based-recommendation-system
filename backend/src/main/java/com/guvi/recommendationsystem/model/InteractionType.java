package com.guvi.recommendationsystem.model;

/**
 * Each new request ID represents one recorded event. Passive activity and negative feedback adapt topic
 * preferences (including MORE_LIKE); explicit positive item actions contribute to item-level ranking and
 * neighborhood evidence.
 * New request IDs remain separate events; only a retry with the same ID is idempotent.
 */
public enum InteractionType {
    VIEW,
    CLICK,
    READ_PROGRESS,
    SAVE,
    PURCHASE,
    LIKE,
    DISLIKE,
    HIDE,
    SHARE,
    SKIP,
    MORE_LIKE,
    SEARCH,
    COMPLETE
}
