package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.Interest;

import java.util.List;
import java.util.Optional;

public interface InterestDao {
    Optional<Interest> findById(long interestId);

    List<Interest> findActive();

    List<Interest> findAll();

    boolean existsById(long interestId);

    Interest insert(String name);

    boolean update(long interestId, String name);

    boolean setActive(long interestId, boolean active);
}
