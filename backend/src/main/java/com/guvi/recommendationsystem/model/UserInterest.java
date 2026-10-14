package com.guvi.recommendationsystem.model;

import java.math.BigDecimal;

public record UserInterest(long interestId, String name, BigDecimal weight) {
}
