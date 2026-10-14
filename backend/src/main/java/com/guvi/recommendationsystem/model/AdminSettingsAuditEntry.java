package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminSettingsAuditEntry(long auditId, String settingKey, BigDecimal oldValue,
                                      BigDecimal newValue, String actorUsername, LocalDateTime changedAt) { }
