package com.guvi.recommendationsystem.model;

import java.util.List;

public record NicheOption(long categoryId, String code, String name, List<TopicOption> topics) {
}
