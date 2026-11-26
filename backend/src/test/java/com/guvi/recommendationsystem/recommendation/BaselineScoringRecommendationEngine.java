package com.guvi.recommendationsystem.recommendation;

import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explainable weighted ranker; values are ranking points, not probabilities. */
public class BaselineScoringRecommendationEngine implements RecommendationEngine {
    private static final double CATEGORY_WEIGHT = 12.0;
    private static final double INTEREST_WEIGHT = 24.0;
    private static final double RELATED_WEIGHT = 10.0;
    private static final double CONTENT_WEIGHT = 8.0;
    private static final double RATING_WEIGHT = 7.0;
    private static final double INTERACTION_WEIGHT = 16.0;
    private static final double POPULARITY_WEIGHT = 10.0;
    private static final double COLLABORATIVE_WEIGHT = 10.0;
    private static final double PURPOSE_WEIGHT = 3.0;
    private static final double EDITOR_PICK_WEIGHT = 5.0;
    private static final double NEGATIVE_PENALTY_WEIGHT = 20.0;
    private static final int MIN_COLLABORATIVE_EVIDENCE = 3;

    private volatile FeedMix feedMix = new FeedMix(0.70, 0.20, 0.10);

    @Autowired
    public void configureFromProperties(@Value("${recommendation.mix.familiar:0.70}") double familiar,
                                        @Value("${recommendation.mix.related:0.20}") double related,
                                        @Value("${recommendation.mix.discovery:0.10}") double discovery) {
        configureMix(familiar, related, discovery);
    }

    public void configureMix(double familiar, double related, double discovery) {
        double sum = familiar + related + discovery;
        if (!Double.isFinite(sum) || familiar < 0 || related < 0 || discovery < 0
                || familiar > 1 || related > 1 || discovery > 1 || Math.abs(sum - 1.0) > 0.0001) {
            throw new IllegalArgumentException("Recommendation mix shares must be between 0 and 1 and sum to 1.");
        }
        feedMix = new FeedMix(familiar, related, discovery);
    }

    public Map<String, Double> publicMix() {
        FeedMix current = feedMix;
        return Map.of("familiar", current.familiar(), "related", current.related(), "discovery", current.discovery());
    }

    @Override
    public List<RecommendationDraft> recommend(List<RecommendationCandidate> candidates, int topN) {
        return recommend(candidates, topN, feedMix);
    }

    @Override
    public List<RecommendationDraft> recommend(List<RecommendationCandidate> candidates, int topN, int exploration) {
        FeedMix base = feedMix;
        double shift = (exploration - 50) / 100.0;
        double retained = 1 - Math.abs(shift);
        return recommend(candidates, topN, new FeedMix(
                base.familiar() * retained + Math.max(0, -shift),
                base.related() * retained,
                base.discovery() * retained + Math.max(0, shift)));
    }

    public List<RecommendationDraft> recommendWithMix(List<RecommendationCandidate> candidates, int topN,
                                                       double familiar, double related, double discovery) {
        double sum = familiar + related + discovery;
        if (!Double.isFinite(sum) || familiar < 0 || related < 0 || discovery < 0
                || familiar > 1 || related > 1 || discovery > 1 || Math.abs(sum - 1.0) > 0.0001) {
            throw new IllegalArgumentException("Recommendation mix shares must be between 0 and 1 and sum to 1.");
        }
        return recommend(candidates, topN, new FeedMix(familiar, related, discovery));
    }

    private List<RecommendationDraft> recommend(List<RecommendationCandidate> candidates, int topN, FeedMix mix) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be positive.");
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ScoredCandidate> scored = candidates.stream()
                .filter(candidate -> candidate != null && (candidate.negativeInteractionSignal() == null
                        || candidate.negativeInteractionSignal().doubleValue() < 1.25))
                .map(this::score)
                .sorted(Comparator.comparingDouble(ScoredCandidate::rawScore).reversed()
                        .thenComparingLong(value -> value.candidate().itemId()))
                .toList();

        int familiarLimit = (int) Math.round(topN * share(mix.familiar(), mix.total()));
        int relatedLimit = (int) Math.round(topN * share(mix.related(), mix.total()));
        int discoveryLimit = Math.max(0, topN - familiarLimit - relatedLimit);
        List<ScoredCandidate> selected = new ArrayList<>(topN);
        appendBucket(scored, selected, "FAMILIAR", familiarLimit);
        appendBucket(scored, selected, "RELATED", relatedLimit);
        appendBucket(scored, selected, "DISCOVERY", discoveryLimit);
        for (ScoredCandidate candidate : scored) {
            if (selected.size() >= topN) break;
            if (!selected.contains(candidate)) addWithCategoryVariety(scored, selected, candidate);
        }
        return selected.stream().limit(topN).map(ScoredCandidate::draft).toList();
    }

    private double share(double value, double total) {
        return Math.max(0, value) / Math.max(0.0001, total);
    }

    private void appendBucket(List<ScoredCandidate> source, List<ScoredCandidate> destination,
                              String bucket, int limit) {
        List<ScoredCandidate> bucketItems = source.stream().filter(value -> value.bucketType().equals(bucket))
                .filter(value -> !destination.contains(value)).toList();
        for (int i = 0; i < limit && !bucketItems.isEmpty(); i++) {
            ScoredCandidate next = bucketItems.stream()
                    .filter(value -> !destination.contains(value))
                    .filter(value -> !sameCategoryAtEnd(destination, value))
                    .findFirst().orElseGet(() -> bucketItems.stream()
                            .filter(value -> !destination.contains(value)).findFirst().orElse(null));
            if (next == null) return;
            destination.add(next);
        }
    }

    private void addWithCategoryVariety(List<ScoredCandidate> source, List<ScoredCandidate> destination,
                                        ScoredCandidate candidate) {
        if (!sameCategoryAtEnd(destination, candidate)) {
            destination.add(candidate);
            return;
        }
        ScoredCandidate alternative = source.stream().filter(value -> !destination.contains(value))
                .filter(value -> !sameCategoryAtEnd(destination, value)).findFirst().orElse(candidate);
        destination.add(alternative);
    }

    private boolean sameCategoryAtEnd(List<ScoredCandidate> destination, ScoredCandidate candidate) {
        if (destination.isEmpty()) return false;
        String category = candidate.candidate().categoryName();
        String previous = destination.getLast().candidate().categoryName();
        return category != null && !category.isBlank() && category.equalsIgnoreCase(previous);
    }

    private ScoredCandidate score(RecommendationCandidate candidate) {
        double category = unit(candidate.categoryPreferenceWeight());
        double interest = unit(candidate.interestMatch());
        double related = unit(candidate.relatedInterestMatch());
        double content = unit(candidate.contentSimilarity());
        double rating = ratingMatch(candidate);
        double positive = nonNegative(candidate.positiveInteractionSignal());
        double negative = nonNegative(candidate.negativeInteractionSignal());
        double positiveActivity = Math.tanh(positive / 2.0);
        double collaborative = candidate.collaborativeEvidenceCount() >= MIN_COLLABORATIVE_EVIDENCE
                ? unit(candidate.collaborativeMatch()) : 0.0;
        double popularity = candidate.popularityEvidenceCount() >= MIN_COLLABORATIVE_EVIDENCE
                ? unit(candidate.popularityMatch()) : 0.0;
        double purpose = unit(candidate.purposeMatch());
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        putPoints(breakdown, "category_preference", CATEGORY_WEIGHT * category);
        putPoints(breakdown, "selected_topic_match", INTEREST_WEIGHT * interest);
        putPoints(breakdown, "related_topic_match", RELATED_WEIGHT * related);
        putPoints(breakdown, "metadata_similarity", CONTENT_WEIGHT * content);
        putPoints(breakdown, "positive_interaction", INTERACTION_WEIGHT * positiveActivity);
        putPoints(breakdown, "rating_affinity", RATING_WEIGHT * rating);
        putPoints(breakdown, "catalog_popularity", POPULARITY_WEIGHT * popularity);
        putPoints(breakdown, "collaborative_evidence", COLLABORATIVE_WEIGHT * collaborative);
        putPoints(breakdown, "onboarding_goal", PURPOSE_WEIGHT * purpose);
        putPoints(breakdown, "editor_pick", candidate.editorPick() ? EDITOR_PICK_WEIGHT : 0.0);
        double positivePoints = breakdown.values().stream().mapToDouble(BigDecimal::doubleValue).sum();
        double penaltyPoints = Math.min(positivePoints, NEGATIVE_PENALTY_WEIGHT * Math.tanh(negative / 2.0));
        putPoints(breakdown, "negative_feedback_penalty", -penaltyPoints);
        BigDecimal stored = breakdown.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        String bucket = interest >= 0.10 || positive > 0 || rating > 0 || category > 0 && related < 0.10 ? "FAMILIAR"
                : related >= 0.10 || content >= 0.10 || category > 0 || collaborative > 0 ? "RELATED" : "DISCOVERY";
        String topic = candidate.matchedTopics() == null || candidate.matchedTopics().isBlank()
                ? "topics you have explored" : candidate.matchedTopics();
        String reasonType;
        String reason;
        if (bucket.equals("DISCOVERY")) {
            if (candidate.editorPick()) {
                reasonType = "EDITOR_PICK";
                reason = "Editor's pick: selected by the RecomAI catalog team as a useful starting resource.";
            } else if (popularity > 0) {
                reasonType = "POPULARITY";
                reason = "Popular in this catalog based on recent positive activity from multiple users.";
            } else {
                reasonType = "COLD_START";
                reason = "Exploratory pick: there is not enough preference or activity evidence to personalize this yet.";
            }
        } else if (bucket.equals("RELATED")) {
            if (collaborative > 0 && related < 0.10 && content < 0.10) {
                reasonType = "COLLABORATIVE";
                reason = "People with overlapping positive item activity also engaged with this resource.";
            } else if (content >= 0.10 && (candidate.contentMatchTerms() != null
                    && !candidate.contentMatchTerms().isBlank())) {
                reasonType = "CONTENT_SIMILARITY";
                reason = "Its description and topics overlap with resources you engaged with: "
                        + candidate.contentMatchTerms() + ".";
            } else {
                reasonType = "RELATED_INTEREST";
                reason = "Related to topics you have explored, while introducing a nearby subject.";
            }
        } else if (positive > 0) {
            reasonType = "RECENT_ACTIVITY";
            reason = "Your recent activity suggests interest in " + topic + ".";
        } else if (interest > 0) {
            reasonType = "PROFILE_MATCH";
            reason = "Recommended because it matches your topic interests: " + topic + ".";
        } else if (category > 0) {
            reasonType = "PROFILE_MATCH";
            String categoryName = candidate.categoryName() == null || candidate.categoryName().isBlank()
                    ? "a category you selected" : candidate.categoryName();
            reason = "Recommended because it matches your selected category: " + categoryName + ".";
        } else if (rating > 0) {
            reasonType = "RATING_AFFINITY";
            reason = "Its catalog rating is close to the ratings you have given in this category.";
        } else {
            reasonType = "ONBOARDING";
            reason = "A starting point based on the area you chose during onboarding.";
        }
        if (negative > 0) {
            reason += " Negative feedback has reduced its score.";
        }
        if (candidate.collaborativeEvidenceCount() > 0 && collaborative == 0) {
            reason += " Similar-user evidence is too sparse to influence this ranking.";
        }
        if (purpose > 0) {
            reason += " It also aligns with your onboarding goal.";
        }
        if (candidate.editorPick() && !reasonType.equals("EDITOR_PICK")) {
            reason += " It is also an editor-picked resource.";
        }
        return new ScoredCandidate(candidate, stored.doubleValue(), new RecommendationDraft(candidate.itemId(),
                candidate.itemTitle(), stored, reason, reasonType, bucket, breakdown), bucket);
    }

    private void putPoints(Map<String, BigDecimal> breakdown, String key, double points) {
        breakdown.put(key, BigDecimal.valueOf(points).setScale(4, RoundingMode.HALF_UP));
    }

    private double ratingMatch(RecommendationCandidate candidate) {
        BigDecimal item = candidate.itemAverageRating();
        if (item == null) return 0;
        BigDecimal personal = candidate.userAverageRating();
        if (personal == null) return 0;
        return Math.max(0, 1 - Math.abs(personal.doubleValue() - item.doubleValue()) / 4.0);
    }

    private double unit(BigDecimal value) {
        return value == null ? 0 : Math.max(0, Math.min(1, value.doubleValue()));
    }

    private double nonNegative(BigDecimal value) {
        return value == null ? 0 : Math.max(0, value.doubleValue());
    }

    private record ScoredCandidate(RecommendationCandidate candidate, double rawScore,
                                   RecommendationDraft draft, String bucketType) {
    }
    private record FeedMix(double familiar, double related, double discovery) {
        private double total() { return familiar + related + discovery; }
    }
}

