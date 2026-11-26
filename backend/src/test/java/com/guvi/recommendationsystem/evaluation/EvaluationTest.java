package com.guvi.recommendationsystem.evaluation;

import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.recommendation.ScoringRecommendationEngine;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class EvaluationTest {
    @Test void chronologicalWindowAndDeduplicationKeepUsersSeparate() {
        Instant t = Instant.EPOCH, later = t.plusSeconds(1);
        var positive = new Evaluation.Event(7,1,later,"SAVE");
        var labels = Evaluation.labels(List.of(positive,positive,new Evaluation.Event(7,2,t,"LIKE"),
                new Evaluation.Event(9,3,later,"CLICK"),new Evaluation.Event(9,4,later,"COMPLETE"),
                new Evaluation.Event(7,5,later.plusSeconds(1),"LIKE")),t,later);
        assertThat(labels).isEqualTo(Map.of(7L,Set.of(1L),9L,Set.of(4L)));
    }

    @Test void macroMetricsCountEmptyListsDuplicatesAndUnreachableItemsCorrectly() {
        var m = new Evaluation.Metrics();
        var catalog = Map.of(1L,"A",2L,"B",3L,"A");
        m.add(List.of(1L,1L,2L),Set.of(2L,3L),catalog,5);
        m.add(List.of(),Set.of(1L),catalog,5);
        m.add(List.of(1L),Set.of(99L),catalog,5);
        double d = Math.log(2)/Math.log(3);
        assertThat(m.users).isEqualTo(2);
        assertThat(m.values()).containsExactly(0.1,0.25,d/(1+d)/2,2.0/3,1.0);
        assertThat(Arrays.stream(new Evaluation.Metrics().values()).allMatch(Double::isNaN)).isTrue();
    }

    @Test void actualHybridAndDeterministicBaselineRankTheSameEligiblePool() {
        var a = candidate(1,0,1,0);
        var b = candidate(2,1,0,0);
        var c = candidate(3,0,1,0);
        var hidden = candidate(4,1,5,2);
        var ranked = Evaluation.rank(new ScoringRecommendationEngine(),List.of(c,b,hidden,a),50);
        assertThat(ranked.hybrid()).containsExactly(2L,1L,3L);
        assertThat(ranked.popularity()).containsExactly(1L,3L,2L);
        assertThat(ranked.categories()).containsOnlyKeys(1L,2L,3L);
    }
    private RecommendationCandidate candidate(long id,int interest,int popularity,int negative) {
        var zero = BigDecimal.ZERO;
        return new RecommendationCandidate(id,"Resource",zero,BigDecimal.valueOf(interest),null,null,
                zero,BigDecimal.valueOf(negative),zero,"Category","",zero,zero,zero,0,zero,popularity);
    }
}
