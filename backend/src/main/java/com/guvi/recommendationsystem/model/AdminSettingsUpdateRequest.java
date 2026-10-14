package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

public record AdminSettingsUpdateRequest(BigDecimal familiarShare, BigDecimal relatedShare,
                                         BigDecimal discoveryShare) { }
