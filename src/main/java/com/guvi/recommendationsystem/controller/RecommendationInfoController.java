package com.guvi.recommendationsystem.controller;

import com.guvi.recommendationsystem.model.RecommendationInfo;
import com.guvi.recommendationsystem.recommendation.ScoringRecommendationEngine;
import com.guvi.recommendationsystem.service.InteractionSignalWeights;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendation-info")
public class RecommendationInfoController {
    private final ScoringRecommendationEngine engine;
    private final InteractionSignalWeights signalWeights;
    public RecommendationInfoController(ScoringRecommendationEngine engine, InteractionSignalWeights signalWeights) {
        this.engine = engine; this.signalWeights = signalWeights;
    }
    @GetMapping
    public RecommendationInfo getInfo() {
        return new RecommendationInfo(engine.publicMix(), signalWeights.publicWeights(),
                "Ranking points explain ordering; they are not a probability or a promise that you will like an item.",
                3, 3);
    }
}
