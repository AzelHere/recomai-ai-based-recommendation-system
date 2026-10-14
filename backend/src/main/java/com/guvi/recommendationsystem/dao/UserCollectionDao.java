package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.UserCollection;
import java.util.List;
import java.util.Optional;

public interface UserCollectionDao {
    List<UserCollection> findByUserId(long userId);
    UserCollection create(long userId, String name);
    boolean belongsToUser(long userId, long collectionId);
    void addItem(long collectionId, long itemId);
    void removeItem(long collectionId, long itemId);
    void delete(long userId, long collectionId);
}
