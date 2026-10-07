package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.InteractionType;

import java.util.List;
import java.util.Optional;

public interface InteractionDao {
    Interaction insert(long userId, long itemId, InteractionType type);

    Optional<Interaction> findById(long interactionId);

    List<Interaction> findRecentByUser(long userId, int limit);
}
