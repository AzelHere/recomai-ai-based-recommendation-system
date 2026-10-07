package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserPreference;

import java.math.BigDecimal;
import java.util.List;

public interface UserPreferenceDao {
    List<UserPreference> findByUserId(long userId);

    void save(long userId, long categoryId, BigDecimal weight);
}
