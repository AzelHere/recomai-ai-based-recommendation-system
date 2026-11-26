package com.guvi.recommendationsystem.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentFeatureScorerTest {
    private final ContentFeatureScorer scorer = new ContentFeatureScorer();
    @Test void selectedTopicsSupportColdStartAndChangedCorpusRefreshesCachedVectors(){
        var docs=List.of(new ContentFeatureScorer.ItemDocument(1,"Java Java programming"),new ContentFeatureScorer.ItemDocument(2,"bread baking"));
        var first=scorer.score(docs,Set.of(),"Java programming");
        assertThat(first).containsKey(1L).doesNotContainKey(2L);assertThat(first.get(1L).score()).isBetween(0.9,1.0);
        assertThat(scorer.score(docs,Set.of(),"Java programming")).isEqualTo(first);
        assertThat(scorer.score(List.of(new ContentFeatureScorer.ItemDocument(1,"bread baking")),Set.of(),"Java programming")).isEmpty();
    }
    @Test void cosineIsBoundedAndThreadSafeForDifferentImmutableProfiles()throws Exception {
        var docs=List.of(new ContentFeatureScorer.ItemDocument(1,"Java programming"),new ContentFeatureScorer.ItemDocument(2,"SQL databases"));
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
            var calls=new java.util.ArrayList<java.util.concurrent.Future<Map<Long,ContentFeatureScorer.Match>>>();
            for(int i=0;i<20;i++){String query=i%2==0?"Java":"SQL";calls.add(executor.submit(()->scorer.score(docs,Set.of(),query)));}
            for(int i=0;i<calls.size();i++){var result=calls.get(i).get();assertThat(result).containsOnlyKeys(i%2==0?1L:2L);assertThat(result.values().iterator().next().score()).isBetween(0.0,1.0);}
        }
    }

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
