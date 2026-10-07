package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserInterest;

import java.math.BigDecimal;
import java.util.List;

public interface UserInterestDao {
    List<UserInterest> findByUserId(long userId);

    void save(long userId, long interestId, BigDecimal weight);

    boolean delete(long userId, long interestId);
}
