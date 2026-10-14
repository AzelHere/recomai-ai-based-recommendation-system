package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.UserCollectionDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.UserCollection;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserCollectionService {
    private final UserCollectionDao collections;
    private final UserDao users;
    private final ItemDao items;
    public UserCollectionService(UserCollectionDao collections, UserDao users, ItemDao items) {
        this.collections = collections; this.users = users; this.items = items;
    }
    public List<UserCollection> list(long userId) { ensureUser(userId); return collections.findByUserId(userId); }
    public UserCollection create(long userId, String rawName) {
        ensureUser(userId);
        String name = rawName == null ? "" : rawName.strip();
        if (name.length() < 1 || name.length() > 80) throw new InvalidUserInputException("Collection name must be 1 to 80 characters.");
        return collections.create(userId, name);
    }
    public void addItem(long userId, long collectionId, long itemId) {
        requireCollection(userId, collectionId);
        if (items.findById(itemId).filter(item -> item.active()).isEmpty()) throw new ItemNotFoundException("An active resource with that ID was not found.");
        collections.addItem(collectionId, itemId);
    }
    public void removeItem(long userId, long collectionId, long itemId) {
        requireCollection(userId, collectionId); collections.removeItem(collectionId, itemId);
    }
    public void delete(long userId, long collectionId) {
        requireCollection(userId, collectionId); collections.delete(userId, collectionId);
    }
    private void requireCollection(long userId, long collectionId) {
        ensureUser(userId);
        if (collectionId < 1 || !collections.belongsToUser(userId, collectionId)) throw new UserNotFoundException("Collection was not found.");
    }
    private void ensureUser(long userId) {
        if (userId < 1 || !users.existsById(userId)) throw new UserNotFoundException("User was not found.");
    }
}
