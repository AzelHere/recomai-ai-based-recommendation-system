package com.guvi.recommendationsystem.recommendation;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class ContentMathBoundaryTest {
    private final ContentFeatureScorer scorer = new ContentFeatureScorer();

    @Test void logarithmicTermFrequencyAndNormalizedCosineMatchAHandCalculatedCorpus() {
        var corpus=List.of(new ContentFeatureScorer.ItemDocument(1,"java java basics"),
                new ContentFeatureScorer.ItemDocument(2,"java sql"),
                new ContentFeatureScorer.ItemDocument(3,"sql basics"));
        var scores=scorer.score(corpus,Set.of(),"JAVA");
        // Every known term has df=2, so their shared smoothed IDF cancels during document normalization.
        double repeatedTf=1+Math.log(2);
        assertThat(scores.get(1L).score()).isCloseTo(repeatedTf/Math.sqrt(repeatedTf*repeatedTf+1),within(1e-12));
        assertThat(scores.get(2L).score()).isCloseTo(1/Math.sqrt(2),within(1e-12));
        assertThat(scores).doesNotContainKey(3L);
        var unknown=scorer.score(corpus,Set.of(),"java unseen");
        double knownIdf=1+Math.log(4.0/3),unknownIdf=1+Math.log(4);
        assertThat(unknown.get(2L).score()).isCloseTo(knownIdf/Math.sqrt(knownIdf*knownIdf+unknownIdf*unknownIdf)/Math.sqrt(2),within(1e-12));
    }

    @Test void zeroVectorsMissingMetadataAndAbsentProfilesNeverProduceFabricatedMatches() {
        var corpus=List.of(new ContentFeatureScorer.ItemDocument(1,null),
                new ContentFeatureScorer.ItemDocument(2,"and the for"));
        assertThat(scorer.score(corpus,Set.of(),"java")).isEmpty();
        assertThat(scorer.score(corpus,Set.of(999L))).isEmpty();
        assertThat(scorer.score(List.of(),Set.of(),"java")).isEmpty();
        assertThat(scorer.score(null,Set.of(),"java")).isEmpty();
    }

    @Test void identicalVectorsHaveUnitCosineAndStableLexicalExplanations() {
        var corpus=List.of(new ContentFeatureScorer.ItemDocument(1,"java sql"),
                new ContentFeatureScorer.ItemDocument(2,"sql java"),
                new ContentFeatureScorer.ItemDocument(3,"baking bread"));
        var matches=scorer.score(corpus,Set.of(1L));
        assertThat(matches).containsOnlyKeys(2L);
        assertThat(matches.get(2L).score()).isCloseTo(1,within(1e-12));
        assertThat(matches.get(2L).overlappingTerms()).isEqualTo("java, sql");
    }
}
