package com.guvi.recommendationsystem.dao;

import java.math.BigDecimal;

public interface ItemInterestDao {
    void save(long itemId, long interestId, BigDecimal weight);

    boolean delete(long itemId, long interestId);
}
