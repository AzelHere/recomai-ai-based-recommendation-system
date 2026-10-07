package com.guvi.recommendationsystem.dao;

public interface UserSignalSummaryDao {
    long countInteractions(long userId);

    long countFeedback(long userId);
}
