package com.guvi.recommendationsystem.evaluation;

import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import com.guvi.recommendationsystem.recommendation.ScoringRecommendationEngine;
import java.util.*;
import java.time.Instant;

final class Evaluation {
    record Event(long user, long item, Instant at, String type) {}
    record Lists(List<Long> hybrid, List<Long> popularity, Map<Long,String> categories) {}

    static Map<Long,Set<Long>> labels(Collection<Event> events, Instant cutoff, Instant end) {
        Map<Long,Set<Long>> result = new TreeMap<>();
        for (var event : events) {
            if (event.user <= 0 || event.item <= 0 || !event.at.isAfter(cutoff) || event.at.isAfter(end)) continue;
            if (Set.of("SAVE", "LIKE", "COMPLETE").contains(event.type)) {
                result.computeIfAbsent(event.user, ignored -> new HashSet<>()).add(event.item);
            }
        }
        return result;
    }

    static Lists rank(ScoringRecommendationEngine engine, List<RecommendationCandidate> candidates, int exploration) {
        var pool = candidates.stream().filter(c -> c.negativeInteractionSignal() == null
                || c.negativeInteractionSignal().doubleValue() < 1.25).toList();
        Map<Long,String> categories = new HashMap<>();
        pool.forEach(c -> categories.put(c.itemId(), c.categoryName()));
        var hybrid = engine.recommend(pool, 10, exploration).stream().map(RecommendationDraft::itemId).toList();
        var baseline = pool.stream().sorted(Comparator.comparingInt(RecommendationCandidate::popularityEvidenceCount)
                .reversed().thenComparingLong(RecommendationCandidate::itemId)).limit(10)
                .map(RecommendationCandidate::itemId).toList();
        return new Lists(hybrid, baseline, categories);
    }

    static final class Metrics {
        int users, diversityUsers;
        double precision, recall, ndcg, diversity;
        final Set<Long> catalog = new HashSet<>(), reached = new HashSet<>();

        void add(List<Long> ranking, Set<Long> positives, Map<Long,String> categories, int k) {
            var relevant = new HashSet<>(positives);
            relevant.retainAll(categories.keySet());
            if (relevant.isEmpty()) return;
            users++;
            catalog.addAll(categories.keySet());
            var top = ranking.stream().filter(categories::containsKey).distinct().limit(k).toList();
            reached.addAll(top);
            int hits = 0, pairs = 0, different = 0;
            double dcg = 0, ideal = 0;
            for (int i = 0; i < Math.min(k, relevant.size()); i++) ideal += discount(i);
            for (int i = 0; i < top.size(); i++) {
                if (relevant.contains(top.get(i))) { hits++; dcg += discount(i); }
                for (int j = 0; j < i; j++) {
                    pairs++;
                    if (!Objects.equals(categories.get(top.get(i)), categories.get(top.get(j)))) different++;
                }
            }
            precision += (double) hits / k;
            recall += (double) hits / relevant.size();
            ndcg += dcg / ideal;
            if (pairs > 0) { diversityUsers++; diversity += (double) different / pairs; }
        }

        double[] values() {
            return new double[]{precision/users, recall/users, ndcg/users,
                    (double) reached.size()/catalog.size(), diversity/diversityUsers};
        }

        private static double discount(int rankIndex) { return Math.log(2) / Math.log(rankIndex + 2); }
    }
}
