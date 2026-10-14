package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Interaction;
import com.guvi.recommendationsystem.model.InteractionType;
import com.guvi.recommendationsystem.model.InteractionWriteResult;

import java.util.List;
import java.util.Optional;

public interface InteractionDao {
    Interaction insert(long userId, long itemId, InteractionType type);

    InteractionWriteResult insertWithRequestId(long userId, long itemId, InteractionType type, String requestId);

    Interaction insertSearch(long userId, String query);

    InteractionWriteResult insertSearchWithRequestId(long userId, String query, String requestId);

    Optional<Interaction> findById(long interactionId);

    List<Interaction> findRecentByUser(long userId, int limit);

    List<Interaction> findCurrentProgressByUser(long userId);

    int deleteAllForUser(long userId);
}
