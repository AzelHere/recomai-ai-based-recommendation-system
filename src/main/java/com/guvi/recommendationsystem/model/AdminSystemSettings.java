package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminSystemSettings(BigDecimal familiarShare, BigDecimal relatedShare,
                                  BigDecimal discoveryShare, LocalDateTime updatedAt) { }
