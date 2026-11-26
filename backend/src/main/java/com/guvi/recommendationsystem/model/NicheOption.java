package com.guvi.recommendationsystem.model;

import java.util.List;

public record NicheOption(long categoryId, String code, String name, List<TopicOption> topics) {
    public NicheOption {
        topics = topics == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(topics));
    }

}
