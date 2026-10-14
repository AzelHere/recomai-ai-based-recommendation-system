package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Feedback;

import java.util.List;
import java.util.Optional;

public interface FeedbackDao {
    List<Feedback> findByUserId(long userId);

    Optional<Feedback> findByUserAndItem(long userId, long itemId);

    Feedback insert(long userId, long itemId, int rating, String comment);

    boolean update(long userId, long itemId, int rating, String comment);
}
