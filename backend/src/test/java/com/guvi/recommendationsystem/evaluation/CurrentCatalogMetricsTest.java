package com.guvi.recommendationsystem.evaluation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class CurrentCatalogMetricsTest {
 @Test void gradedMetricsRespectRankDiscountsShortListsAndUndefinedRecall(){
  var grades=Map.of(1L,3,2L,1,3L,0);var categories=Map.of(1L,"A",2L,"B",3L,"B");
  var m=CurrentCatalogEvaluation.metrics(List.of(3L,2L,1L),grades,categories,3);
  double expected=(1/(Math.log(3)/Math.log(2))+7.0/2)/(7+1/(Math.log(3)/Math.log(2)));
  assertThat(m.precision()).isCloseTo(2.0/3,within(1e-12));assertThat(m.recall()).isEqualTo(1);assertThat(m.ndcg()).isCloseTo(expected,within(1e-12));assertThat(m.mrr()).isEqualTo(.5);assertThat(m.categoryDiversity()).isCloseTo(2.0/3,within(1e-12));
  assertThat(CurrentCatalogEvaluation.metrics(List.of(1L),grades,categories,5).precision()).isEqualTo(.2);
  assertThat(CurrentCatalogEvaluation.metrics(List.of(3L),Map.of(3L,0),categories,5).recall()).isNaN();
  assertThatThrownBy(()->CurrentCatalogEvaluation.metrics(List.of(),grades,categories,0)).isInstanceOf(IllegalArgumentException.class);
 }
}
