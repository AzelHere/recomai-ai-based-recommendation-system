package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

public record AdminSettingsPreviewRequest(long sampleUserId, BigDecimal familiarShare,
                                          BigDecimal relatedShare, BigDecimal discoveryShare) { }
