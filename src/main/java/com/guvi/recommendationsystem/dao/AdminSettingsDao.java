package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.model.AdminSettingsAuditEntry;
import com.guvi.recommendationsystem.model.AdminSystemSettings;
import java.math.BigDecimal;
import java.util.List;

public interface AdminSettingsDao {
    AdminSystemSettings getSettings();
    void save(String key, BigDecimal value, long actorUserId);
    List<AdminSettingsAuditEntry> findAudit(int limit);
}
