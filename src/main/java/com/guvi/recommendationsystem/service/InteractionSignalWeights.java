package com.guvi.recommendationsystem.service;

import com.guvi.recommendationsystem.model.InteractionType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.LinkedHashMap;

/** Central signal weights; application.properties can tune these without changing event handling. */
@Component
public class InteractionSignalWeights {
    private final Map<InteractionType, Double> weights = new EnumMap<>(InteractionType.class);

    public InteractionSignalWeights(
            @Value("${recommendation.signals.view:0.15}") double view,
            @Value("${recommendation.signals.click:0.50}") double click,
            @Value("${recommendation.signals.read-progress:0.65}") double readProgress,
            @Value("${recommendation.signals.save:1.00}") double save,
            @Value("${recommendation.signals.purchase:1.25}") double purchase,
            @Value("${recommendation.signals.like:1.00}") double like,
            @Value("${recommendation.signals.dislike:-1.25}") double dislike,
            @Value("${recommendation.signals.hide:-1.50}") double hide,
            @Value("${recommendation.signals.share:1.50}") double share,
            @Value("${recommendation.signals.skip:-0.25}") double skip,
            @Value("${recommendation.signals.more-like:1.25}") double moreLike,
            @Value("${recommendation.signals.search:0.12}") double search) {
        weights.put(InteractionType.VIEW, view);
        weights.put(InteractionType.CLICK, click);
        weights.put(InteractionType.READ_PROGRESS, readProgress);
        weights.put(InteractionType.SAVE, save);
        weights.put(InteractionType.PURCHASE, purchase);
        weights.put(InteractionType.LIKE, like);
        weights.put(InteractionType.DISLIKE, dislike);
        weights.put(InteractionType.HIDE, hide);
        weights.put(InteractionType.SHARE, share);
        weights.put(InteractionType.SKIP, skip);
        weights.put(InteractionType.MORE_LIKE, moreLike);
        weights.put(InteractionType.SEARCH, search);
        weights.put(InteractionType.COMPLETE, 0.0);
    }

    public double weight(InteractionType type) {
        return weights.getOrDefault(type, 0.0);
    }

    public boolean isNegative(InteractionType type) {
        return weight(type) < 0;
    }

    public Map<String, Double> publicWeights() {
        Map<String, Double> result = new LinkedHashMap<>();
        weights.forEach((type, value) -> result.put(type.name(), value));
        return Map.copyOf(result);
    }
}
