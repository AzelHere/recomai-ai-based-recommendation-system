package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.dao.AdminSettingsDao;
import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.exception.UserNotFoundException;
import com.guvi.recommendationsystem.model.AdminSettingsAuditEntry;
import com.guvi.recommendationsystem.model.AdminSettingsUpdateRequest;
import com.guvi.recommendationsystem.model.AdminSettingsPreviewRequest;
import com.guvi.recommendationsystem.model.AdminSystemSettings;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import com.guvi.recommendationsystem.recommendation.ScoringRecommendationEngine;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class AdminSettingsService {
    private static final BigDecimal TOTAL = BigDecimal.ONE;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.0001");
    private final AdminSettingsDao settingsDao;
    private final RecommendationDao recommendationDao;
    private final UserDao userDao;
    private final ScoringRecommendationEngine engine;

    public AdminSettingsService(AdminSettingsDao settingsDao, RecommendationDao recommendationDao,
                                UserDao userDao, ScoringRecommendationEngine engine) {
        this.settingsDao = settingsDao; this.recommendationDao = recommendationDao;
        this.userDao = userDao; this.engine = engine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadPersistedMix() { apply(settingsDao.getSettings()); }

    public AdminSystemSettings getSettings() { return settingsDao.getSettings(); }
    public List<AdminSettingsAuditEntry> getAudit(int limit) {
        if (limit < 1 || limit > 100) throw new InvalidUserInputException("Audit limit must be between 1 and 100.");
        return settingsDao.findAudit(limit);
    }

    @Transactional
    public AdminSystemSettings update(AdminSettingsUpdateRequest request, long actorUserId) {
        validate(request == null ? null : request.familiarShare(), request == null ? null : request.relatedShare(),
                request == null ? null : request.discoveryShare());
        if (actorUserId < 1) throw new InvalidUserInputException("An administrator is required to update settings.");
        settingsDao.save("familiar", scale(request.familiarShare()), actorUserId);
        settingsDao.save("related", scale(request.relatedShare()), actorUserId);
        settingsDao.save("discovery", scale(request.discoveryShare()), actorUserId);
        AdminSystemSettings saved = settingsDao.getSettings();
        apply(saved);
        return saved;
    }

    public List<RecommendationDraft> preview(AdminSettingsPreviewRequest request) {
        if (request == null) throw new InvalidUserInputException("Preview settings and a sample user are required.");
        validate(request.familiarShare(), request.relatedShare(), request.discoveryShare());
        if (request.sampleUserId() < 1 || !userDao.existsById(request.sampleUserId())) {
            throw new UserNotFoundException("Sample user was not found.");
        }
        return engine.recommendWithMix(recommendationDao.findCandidates(request.sampleUserId()), 10,
                request.familiarShare().doubleValue(), request.relatedShare().doubleValue(), request.discoveryShare().doubleValue());
    }

    private void apply(AdminSystemSettings settings) {
        if (settings != null && settings.familiarShare() != null && settings.relatedShare() != null && settings.discoveryShare() != null) {
            engine.configureMix(settings.familiarShare().doubleValue(), settings.relatedShare().doubleValue(),
                    settings.discoveryShare().doubleValue());
        }
    }
    private void validate(BigDecimal familiar, BigDecimal related, BigDecimal discovery) {
        if (familiar == null || related == null || discovery == null
                || familiar.signum() < 0 || related.signum() < 0 || discovery.signum() < 0
                || familiar.compareTo(TOTAL) > 0 || related.compareTo(TOTAL) > 0 || discovery.compareTo(TOTAL) > 0) {
            throw new InvalidUserInputException("Each recommendation share must be between 0 and 1.");
        }
        if (familiar.add(related).add(discovery).subtract(TOTAL).abs().compareTo(TOLERANCE) > 0) {
            throw new InvalidUserInputException("Familiar, related, and discovery shares must add up to 100%.");
        }
    }
    private BigDecimal scale(BigDecimal value) { return value.setScale(4, java.math.RoundingMode.HALF_UP); }
}
