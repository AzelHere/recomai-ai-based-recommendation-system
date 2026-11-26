package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.InterestDao;
import com.guvi.recommendationsystem.dao.ItemDao;
import com.guvi.recommendationsystem.dao.ItemInterestDao;
import com.guvi.recommendationsystem.exception.InterestNotFoundException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.ItemNotFoundException;
import com.guvi.recommendationsystem.model.CatalogItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ItemInterestService {
    private final ItemDao itemDao;
    private final InterestDao interestDao;
    private final ItemInterestDao itemInterestDao;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;
    private void catalogChanged(){if(events!=null)events.publishEvent(new com.guvi.recommendationsystem.catalog.CatalogImported(0));}


    public ItemInterestService(ItemDao itemDao, InterestDao interestDao, ItemInterestDao itemInterestDao) {
        this.itemDao = itemDao;
        this.interestDao = interestDao;
        this.itemInterestDao = itemInterestDao;
    }

    @org.springframework.transaction.annotation.Transactional
    public void associate(long itemId, long interestId, BigDecimal weight) {
        validateIds(itemId, interestId);
        if (weight == null || weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidUserInputException("Interest relevance must be between 0 and 1.");
        }
        CatalogItem item = itemDao.findById(itemId)
                .filter(CatalogItem::active)
                .orElseThrow(() -> new ItemNotFoundException("An active item with that ID was not found."));
        if (interestDao.findById(interestId).filter(interest -> interest.active()).isEmpty()) {
            throw new InterestNotFoundException("An active interest with that ID was not found.");
        }
        itemInterestDao.save(item.itemId(), interestId, weight);catalogChanged();
    }

    @org.springframework.transaction.annotation.Transactional
    public void remove(long itemId, long interestId) {
        validateIds(itemId, interestId);
        if (itemDao.findById(itemId).isEmpty()) {
            throw new ItemNotFoundException("Item was not found.");
        }
        if (!interestDao.existsById(interestId)) {
            throw new InterestNotFoundException("Interest was not found.");
        }
        itemInterestDao.delete(itemId, interestId);catalogChanged();
    }

    private void validateIds(long itemId, long interestId) {
        if (itemId <= 0 || interestId <= 0) {
            throw new InvalidUserInputException("Item and interest IDs must be positive.");
        }
    }
}
