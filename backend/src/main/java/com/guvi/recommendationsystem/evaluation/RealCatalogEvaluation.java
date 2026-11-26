package com.guvi.recommendationsystem.evaluation;

import com.guvi.recommendationsystem.catalog.*;
import com.guvi.recommendationsystem.recommendation.*;
import com.guvi.recommendationsystem.model.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.math.BigDecimal;

/** Fixed independently-from-scores editorial qrels over genuine metadata. Not independent human or user-outcome labels. */
public final class RealCatalogEvaluation {
    private record Query(String id,String query,String category,String topic,Map<String,Integer> grades) { }
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: RealCatalogEvaluation <data directory>");
        Path data=Path.of(args[0]),evaluation=data.resolve("evaluation");var json=JsonMapper.builder().build();
        var records=json.readValue(Files.readString(data.resolve("processed/educational-resources.json")),EducationalResource[].class);
        Map<String,EducationalResource> resources=new LinkedHashMap<>();Map<String,Long> ids=new LinkedHashMap<>();List<ContentFeatureScorer.ItemDocument> corpus=new ArrayList<>();long next=1;
        for(var r:records){String key=r.sourceId()+"/"+r.externalId();resources.put(key,r);ids.put(key,next);corpus.add(new ContentFeatureScorer.ItemDocument(next++,r.title()+" "+Objects.toString(r.description(),"")+" "+String.join(" ",r.topics())));}
        var queries=new LinkedHashMap<String,Query>();var pool=new LinkedHashSet<String>();
        for(String line:Files.readAllLines(evaluation.resolve("catalog-judgments.csv")).subList(1,211)){
            String[] v=line.split(",",7);var query=queries.computeIfAbsent(v[0],id->new Query(id,v[1],v[2],v[3],new LinkedHashMap<>()));query.grades().put(v[4],Integer.valueOf(v[5]));pool.add(v[4]);
        }
        var scorer=new ContentFeatureScorer();var ranker=new ScoringRecommendationEngine();
        var results=new StringBuilder("dataset,query_id,algorithm,k,judged_candidates,precision,recall,ndcg,mrr,duplicate_rate\n");
        var rankings=new StringBuilder("query_id\talgorithm\trank\tresource_key\ttitle\tgrade\tscore\n");var coverage=new HashMap<String,Set<Long>>();
        long buildStart=System.nanoTime();scorer.score(corpus,Set.of(),"Java");double buildMs=(System.nanoTime()-buildStart)/1e6;
        for(var query:queries.values())for(String algorithm:List.of("phase3_no_history","selected_topic_tfidf")) {
            var matches=algorithm.equals("selected_topic_tfidf")?scorer.score(corpus,Set.of(),query.topic()):Map.<Long,ContentFeatureScorer.Match>of();
            var candidates=new ArrayList<RecommendationCandidate>();
            for(String key:pool){var r=resources.get(key);long id=ids.get(key);var match=matches.get(id);double exact=r.topics().stream().anyMatch(t->t.equalsIgnoreCase(query.topic()))?1:0;
                if(query.topic().equals("Java")&&r.categoryCode().equals("TECHNOLOGY")&&r.topics().contains("Computer Science")&&r.title().matches("(?is).*\\bJava\\b.*"))exact=1;
                if(query.topic().equals("Machine Learning")&&r.categoryCode().equals("TECHNOLOGY")&&r.topics().contains("Artificial Intelligence")&&(r.title()+" "+r.description()).toLowerCase(Locale.ROOT).contains("machine learning"))exact=1;
                candidates.add(new RecommendationCandidate(id,r.title(),n(r.categoryCode().equals(query.category())?1:0),n(exact),null,null,n(0),n(0),n(.5),r.categoryCode(),exact>0?query.topic():"",n(0),n(0),n(0),0,n(0),0,n(match==null?0:match.score()),match==null?"":match.overlappingTerms()));
            }
            var ranked=ranker.recommend(candidates,10);var reverse=new HashMap<Long,String>();ids.forEach((k,v)->reverse.put(v,k));
            for(int i=0;i<ranked.size();i++){var d=ranked.get(i);String key=reverse.get(d.itemId());rankings.append(query.id()).append('\t').append(algorithm).append('\t').append(i+1).append('\t').append(key).append('\t').append(d.itemTitle()).append('\t').append(query.grades().get(key)).append('\t').append(d.score()).append('\n');}
            for(int k:List.of(5,10)){var top=ranked.stream().limit(k).toList();int relevant=0,first=0;double dcg=0,idcg=0;var ideal=query.grades().values().stream().sorted(Comparator.reverseOrder()).limit(k).toList();long total=query.grades().values().stream().filter(g->g>0).count();
                for(int i=0;i<top.size();i++){int grade=query.grades().get(reverse.get(top.get(i).itemId()));if(grade>0){relevant++;if(first==0)first=i+1;}dcg+=(Math.pow(2,grade)-1)/(Math.log(i+2)/Math.log(2));}for(int i=0;i<ideal.size();i++)idcg+=(Math.pow(2,ideal.get(i))-1)/(Math.log(i+2)/Math.log(2));
                coverage.computeIfAbsent(algorithm+":"+k,s->new HashSet<>()).addAll(top.stream().map(RecommendationDraft::itemId).toList());
                results.append("genuine_catalog_editorial,").append(query.id()).append(',').append(algorithm).append(',').append(k).append(',').append(pool.size()).append(',').append((double)relevant/k).append(',').append((double)relevant/total).append(',').append(dcg/idcg).append(',').append(first==0?0:1.0/first).append(",0\n");
            }
        }
        Files.writeString(evaluation.resolve("catalog-results.csv"),results);Files.writeString(evaluation.resolve("catalog-rankings.tsv"),rankings);
        var timings=new ArrayList<Double>();for(int i=0;i<220;i++){long start=System.nanoTime();scorer.score(corpus,Set.of(),"Java programming");if(i>=20)timings.add((System.nanoTime()-start)/1e6);}Collections.sort(timings);
        var stats=new LinkedHashMap<String,Object>();stats.put("corpusResources",records.length);stats.put("judgedPool",pool.size());stats.put("queries",queries.size());stats.put("indexBuildMs",buildMs);stats.put("warmQueryMedianMs",timings.get(100));stats.put("warmQueryP95Ms",timings.get(189));stats.put("usedHeapBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());stats.put("maxHeapBytes",Runtime.getRuntime().maxMemory());stats.put("judgmentsSha256",RealDataAcquisition.sha(Files.readAllBytes(evaluation.resolve("catalog-judgments.csv"))));stats.put("catalogSha256",RealDataAcquisition.sha(Files.readAllBytes(data.resolve("processed/educational-resources.json"))));
        var coverageStats=new TreeMap<String,Double>();coverage.forEach((key,value)->coverageStats.put(key,(double)value.size()/pool.size()));stats.put("judgedPoolCoverage",coverageStats);
        Files.writeString(evaluation.resolve("catalog-evaluation-manifest.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(stats));
        System.out.println(json.writeValueAsString(stats));
    }
    private static BigDecimal n(double value){return BigDecimal.valueOf(value);}
}
