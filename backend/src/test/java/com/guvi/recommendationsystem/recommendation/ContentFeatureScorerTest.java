package com.guvi.recommendationsystem.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContentFeatureScorerTest {
    private final ContentFeatureScorer scorer = new ContentFeatureScorer();

    @Test
    void tfIdfCosineRanksMetadataRelatedToPositiveHistoryHigher() {
        var docs = List.of(
                new ContentFeatureScorer.ItemDocument(1, "Java Spring backend service development"),
                new ContentFeatureScorer.ItemDocument(2, "Java Spring API backend programming guide"),
                new ContentFeatureScorer.ItemDocument(3, "Bread baking recipes and cooking tips"));

        var matches = scorer.score(docs, Set.of(1L));

        assertThat(matches).containsKey(2L).doesNotContainKey(3L);
        assertThat(matches.get(2L).overlappingTerms()).contains("java");
        assertThat(matches).doesNotContainKey(1L);
    }

    @Test
    void newUsersWithoutPositiveHistoryDoNotReceiveFabricatedTextSimilarity() {
        var docs = List.of(new ContentFeatureScorer.ItemDocument(1, "Java backend guide"));

        assertThat(scorer.score(docs, Set.of())).isEmpty();
    }
}
