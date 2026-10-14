package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.InterestProfile;
import com.guvi.recommendationsystem.model.InteractionType;

public interface UserTopicPreferenceDao {
    void applyInteraction(long userId, long itemId, InteractionType type, double signalWeight, int halfLifeDays);
    void applySearch(long userId, String query, double signalWeight, int halfLifeDays);
    InterestProfile findProfile(long userId);

    int deleteAllForUser(long userId);
}
