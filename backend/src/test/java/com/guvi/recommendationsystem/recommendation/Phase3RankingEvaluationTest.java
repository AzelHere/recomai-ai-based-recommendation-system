package com.guvi.recommendationsystem.recommendation;

import com.guvi.recommendationsystem.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Deterministic synthetic feature/judgment fixtures, not measured user satisfaction. */
class Phase3RankingEvaluationTest {
    private RecommendationCandidate candidate(long id,String title,String category,double topic,double categoryMatch,double negative) {
        return new RecommendationCandidate(id,title,BigDecimal.valueOf(categoryMatch),BigDecimal.valueOf(topic),null,null,BigDecimal.ZERO,BigDecimal.valueOf(negative),BigDecimal.valueOf(.8),category,"Programming",BigDecimal.ZERO,BigDecimal.ZERO);
    }
    @Test void diversityCannotReplaceRelevantResourcesWithWeakUnrelatedOnes() {
        var pool=List.of(candidate(1,"Getting Started with Java","Technology",1,1,0),candidate(2,"Spring Guides","Technology",.95,1,0),candidate(3,"CS50x","Technology",.9,1,0),candidate(4,"Personal Finance Basics","Business & Finance",.01,.05,0));
        assertThat(new ScoringRecommendationEngine().recommend(pool,3)).extracting(RecommendationDraft::itemId).containsExactly(1L,2L,3L);
        assertThat(new BaselineScoringRecommendationEngine().recommend(pool,3)).extracting(RecommendationDraft::itemId).contains(4L);
    }
    @Test void nearTiesRetainCategoryVarietyAndRankingRemainsDeterministic() {
        var pool=List.of(candidate(1,"A","Technology",1,1,0),candidate(2,"B","Technology",1,1,0),candidate(3,"C","Business & Finance",1,1,0));
        var engine=new ScoringRecommendationEngine();assertThat(engine.recommend(pool,3)).extracting(RecommendationDraft::itemId).containsExactly(1L,3L,2L);
        assertThat(engine.recommend(pool,3)).isEqualTo(engine.recommend(pool,3));
    }
    @Test void duplicateIdsAndMissingMetadataCannotProduceDuplicateStepsOrInventedSignals() {
        var item=candidate(1,"A","",0,0,0);var missing=new RecommendationCandidate(2,null,null,null,null,null,null,null);
        var result=new ScoringRecommendationEngine().recommend(Arrays.asList(item,item,null,missing),5);
        assertThat(result).hasSize(2);assertThat(result).allSatisfy(row->{assertThat(row.reason()).contains("not enough preference or activity evidence");assertThat(row.score()).isZero();});
        assertThat(new ScoringRecommendationEngine().recommend(List.of(),5)).isEmpty();
    }
    @Test void explicitFeedbackSaturatesAndNegativeEvidenceRemainsTruthfullyExplained() {
        var engine=new ScoringRecommendationEngine();var negative=candidate(1,"A","Technology",1,1,.5);var hidden=candidate(2,"B","Technology",1,1,1.5);
        var ranked=engine.recommend(List.of(negative,hidden),5);assertThat(ranked).hasSize(1);assertThat(ranked.getFirst().reason()).contains("Negative feedback has reduced its score");
        var positive=new RecommendationCandidate(3,"C",BigDecimal.ZERO,BigDecimal.ZERO,null,null,BigDecimal.valueOf(1000000),BigDecimal.ZERO);
        assertThat(engine.recommend(List.of(positive),1).getFirst().scoreBreakdown().get("positive_interaction")).isEqualByComparingTo("16");
    }
    @Test void compareFrozenBaselineAndImprovedRankerWithExplicitSyntheticJudgments() throws Exception {
        var catalog=new ArrayList<String[]>();
        catalog.add(new String[]{"Technology","Getting Started with Java"});catalog.add(new String[]{"Technology","Spring Guides: Building a RESTful Web Service"});
        try(var input=getClass().getResourceAsStream("/catalog/curated-resources.tsv")) {
            var lines=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().skip(1).map(line->line.split("\t")).toList();
            for(String category:List.of("Technology","Business & Finance","Creative & Lifestyle","Learning & Career","Health & Fitness")) lines.stream().filter(row->row[0].equals(category)).limit(6).forEach(row->catalog.add(new String[]{row[0],row[1]}));
        }
        var report=new StringBuilder("scenario,engine,k,precision,recall,ndcg,mrr,list_catalog_fraction,category_diversity,duplicate_rate,median_rank_ms,p95_rank_ms\n");
        var coverage=new HashMap<String,Set<Long>>();
        var judgments=new StringBuilder("scenario\titem_id\ttitle\tcategory\tsynthetic_topic_match\tsynthetic_category_match\tnegative\tjudged_relevance\n");
        String[] scenarios={"Java beginner (Programming fallback)","DSA preparation (Programming fallback)","Business and finance","Creative learning","Multiple interests","Negative feedback","No history or onboarding"};
        for(int scenario=0;scenario<scenarios.length;scenario++) {
            var pool=new ArrayList<RecommendationCandidate>();var labels=new HashMap<Long,Integer>();var categories=new HashMap<Long,String>();
            for(int i=0;i<catalog.size();i++) {
                var item=catalog.get(i);long id=i+1;String category=item[0];boolean relevant=scenario<2?category.equals("Technology"):scenario==2?category.equals("Business & Finance"):scenario==3?category.equals("Creative & Lifestyle"):scenario==4?(category.equals("Technology")||category.equals("Business & Finance")):scenario==5?category.equals("Technology"):false;
                double topic=scenario==6?0:relevant?.85:.01,categoryMatch=scenario==6?0:relevant?1:.05,negative=scenario==5&&i==0?1.5:0;
                // Judgments are explicit coarse area relevance, not claims about Java/DSA prerequisites.
                int label=relevant && negative==0?2:0;
                labels.put(id,label);categories.put(id,category);pool.add(candidate(id,item[1],category,topic,categoryMatch,negative));
                judgments.append(String.join("\t",scenarios[scenario],""+id,item[1],category,""+topic,""+categoryMatch,""+negative,""+label)).append('\n');
            }
            for(var entry:Map.<String,RecommendationEngine>of("baseline",new BaselineScoringRecommendationEngine(),"improved",new ScoringRecommendationEngine()).entrySet()) {
                var engine=entry.getValue();var ranked=engine.recommend(pool,5);double[] times=new double[200];for(int n=0;n<220;n++){long start=System.nanoTime();engine.recommend(pool,5);if(n>=20)times[n-20]=(System.nanoTime()-start)/1e6;}Arrays.sort(times);
                ranked.forEach(row->coverage.computeIfAbsent(entry.getKey(),ignored->new HashSet<>()).add(row.itemId()));
                long relevantCount=labels.values().stream().filter(v->v>0).count();double hits=0,dcg=0,idcg=0,mrr=0,pairs=0,different=0;
                for(int i=0;i<ranked.size();i++){int label=labels.get(ranked.get(i).itemId());if(label>0){hits++;if(mrr==0)mrr=1.0/(i+1);}dcg+=(Math.pow(2,label)-1)/(Math.log(i+2)/Math.log(2));for(int j=0;j<i;j++){pairs++;if(!categories.get(ranked.get(i).itemId()).equals(categories.get(ranked.get(j).itemId())))different++;}}
                var ideal=labels.values().stream().sorted(Comparator.reverseOrder()).limit(5).toList();for(int i=0;i<ideal.size();i++)idcg+=(Math.pow(2,ideal.get(i))-1)/(Math.log(i+2)/Math.log(2));
                long unique=ranked.stream().map(RecommendationDraft::itemId).distinct().count();
                report.append(String.join(",",scenarios[scenario],entry.getKey(),"5",format(relevantCount==0?Double.NaN:hits/5),format(relevantCount==0?Double.NaN:hits/relevantCount),format(idcg==0?Double.NaN:dcg/idcg),format(relevantCount==0?Double.NaN:mrr),format((double)unique/catalog.size()),format(pairs==0?Double.NaN:different/pairs),format(1-(double)unique/Math.max(1,ranked.size())),format(times[100]),format(times[189]))).append('\n');
            }
        }
        Path output=Path.of("target/phase3-evaluation");Files.createDirectories(output);Files.writeString(output.resolve("ranking-comparison.csv"),report);Files.writeString(output.resolve("synthetic-judgments.tsv"),judgments);
        var global=new StringBuilder("engine,catalog_items,unique_recommended,catalog_coverage\n");coverage.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->global.append(entry.getKey()).append(',').append(catalog.size()).append(',').append(entry.getValue().size()).append(',').append(format((double)entry.getValue().size()/catalog.size())).append('\n'));Files.writeString(output.resolve("catalog-coverage.csv"),global);
        assertThat(report.toString().lines().count()).isEqualTo(15);
    }
    private String format(double value){return Double.isFinite(value)?String.format(Locale.ROOT,"%.6f",value):"N/A";}
}
