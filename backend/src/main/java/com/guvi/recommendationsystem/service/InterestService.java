package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.InterestDao;
import com.guvi.recommendationsystem.exception.DuplicateCatalogEntryException;
import com.guvi.recommendationsystem.exception.InterestNotFoundException;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.Interest;
import com.guvi.recommendationsystem.model.InterestRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InterestService {
    private final InterestDao interestDao;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;
    private void catalogChanged(){if(events!=null)events.publishEvent(new com.guvi.recommendationsystem.catalog.CatalogImported(0));}


    public InterestService(InterestDao interestDao) {
        this.interestDao = interestDao;
    }

    public List<Interest> getActive() {
        return interestDao.findActive();
    }

    public List<Interest> getAll() {
        return interestDao.findAll();
    }

    @org.springframework.transaction.annotation.Transactional
    public Interest create(InterestRequest request) {
        String name = validate(request);
        try {
            var created=interestDao.insert(name);catalogChanged();return created;
        } catch (DuplicateKeyException exception) {
            throw new DuplicateCatalogEntryException("An interest with that name already exists.");
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public Interest update(long interestId, InterestRequest request) {
        validateId(interestId);
        String name = validate(request);
        if (!interestDao.existsById(interestId)) {
            throw new InterestNotFoundException("Interest was not found.");
        }
        try {
            interestDao.update(interestId, name);catalogChanged();
        } catch (DuplicateKeyException exception) {
            throw new DuplicateCatalogEntryException("An interest with that name already exists.");
        }
        return get(interestId);
    }

    @org.springframework.transaction.annotation.Transactional
    public Interest activate(long interestId) {
        return setActive(interestId, true);
    }

    @org.springframework.transaction.annotation.Transactional
    public Interest deactivate(long interestId) {
        return setActive(interestId, false);
    }

    public Interest get(long interestId) {
        validateId(interestId);
        return interestDao.findById(interestId)
                .orElseThrow(() -> new InterestNotFoundException("Interest was not found."));
    }

    private Interest setActive(long interestId, boolean active) {
        Interest existing = get(interestId);
        if (existing.active() == active) {
            return existing;
        }
        interestDao.setActive(interestId, active);catalogChanged();
        return get(interestId);
    }

    private String validate(InterestRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new InvalidUserInputException("Interest name is required.");
        }
        String name = request.name().trim();
        if (name.length() > 100) {
            throw new InvalidUserInputException("Interest name must be 100 characters or fewer.");
        }
        return name;
    }

    private void validateId(long interestId) {
        if (interestId <= 0) {
            throw new InvalidUserInputException("Interest ID must be positive.");
        }
    }
}
