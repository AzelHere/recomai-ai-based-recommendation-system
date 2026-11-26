package com.guvi.recommendationsystem.evaluation;

import com.guvi.recommendationsystem.RecommendationSystemApplication;
import com.guvi.recommendationsystem.dao.RecommendationDao;
import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import com.guvi.recommendationsystem.recommendation.ScoringRecommendationEngine;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** Isolated editorial-pool evaluation using the production DAO, text features and hybrid ranker.
 * The labels are declared metadata proxies, not independent human judgments or observed learning outcomes.
 */
public final class CurrentCatalogEvaluation {
    record Scores(double precision, double recall, double ndcg, double mrr, double categoryDiversity) { }

    static Scores metrics(List<Long> ranking, Map<Long,Integer> grades, Map<Long,String> categories, int k) {
        if(k<1)throw new IllegalArgumentException("K must be positive");
        var top=ranking.stream().distinct().limit(k).toList();
        long relevant=grades.values().stream().filter(g->g>0).count();
        double dcg=0,ideal=0;int hits=0,first=0,pairs=0,different=0;
        var best=grades.values().stream().sorted(Comparator.reverseOrder()).limit(k).toList();
        for(int i=0;i<best.size();i++)ideal+=(Math.pow(2,best.get(i))-1)/(Math.log(i+2)/Math.log(2));
        for(int i=0;i<top.size();i++){
            int grade=grades.getOrDefault(top.get(i),0);
            if(grade>0){hits++;if(first==0)first=i+1;}
            dcg+=(Math.pow(2,grade)-1)/(Math.log(i+2)/Math.log(2));
            for(int j=0;j<i;j++){pairs++;if(!Objects.equals(categories.get(top.get(i)),categories.get(top.get(j))))different++;}
        }
        return new Scores((double)hits/k,relevant==0?Double.NaN:(double)hits/relevant,
                ideal==0?Double.NaN:dcg/ideal,first==0?0:1.0/first,pairs==0?Double.NaN:(double)different/pairs);
    }

    private static String cell(Object value){return Objects.toString(value,"").replace('\t',' ').replace('\r',' ').replace('\n',' ');}
    private static String number(double value){return Double.isFinite(value)?Double.toString(value):"N/A";}
    private static String key(Object url){return Objects.toString(url,"").replaceFirst("/$","");}

    public static void main(String[] args) throws Exception {
        if(args.length!=2||!System.getenv().getOrDefault("MYSQL_DATABASE","").matches("recomai_quality_[0-9a-f]{12}"))
            throw new IllegalArgumentException("This evaluator requires its explicit isolated quality fixture database.");
        var configured=java.net.URI.create(System.getenv().getOrDefault("DB_URL","").replaceFirst("^jdbc:",""));
        if(!("/"+System.getenv("MYSQL_DATABASE")).equals(configured.getPath()))throw new IllegalArgumentException("Configured database does not match the isolated fixture");
        var json=JsonMapper.builder().build();byte[] input=Files.readAllBytes(Path.of(args[0]));
        if(input.length>2_000_000)throw new IllegalArgumentException("Oversized evaluation input");
        var fixture=json.readTree(input);Path output=Path.of(args[1]);Files.createDirectories(output);
        try(var context=new SpringApplicationBuilder(RecommendationSystemApplication.class).web(WebApplicationType.NONE).run()){
            var jdbc=context.getBean(JdbcTemplate.class);
            if(!System.getenv("MYSQL_DATABASE").equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))throw new IllegalStateException("Database guard failed");
            var dao=context.getBean(RecommendationDao.class);var ranker=context.getBean(ScoringRecommendationEngine.class);
            var metadata=new HashMap<Long,Map<String,Object>>();var byUrl=new HashMap<String,Long>();
            jdbc.queryForList("SELECT item_id,title,description,external_url,source_name,item_type,category_id FROM items WHERE is_active=TRUE").forEach(row->{long id=((Number)row.get("item_id")).longValue();metadata.put(id,row);byUrl.put(key(row.get("external_url")),id);});
            if(metadata.size()!=fixture.path("catalogResources").asInt())throw new IllegalStateException("Evaluation catalog mismatch");
            var results=new StringBuilder("profile,algorithm,k,judgedCandidates,positiveJudgments,precision,recallJudgedPool,ndcg,mrr,categoryDiversity\n");
            var rankings=new StringBuilder("profile\talgorithm\trank\tcanonicalUrl\ttitle\tgrade\ttype\tprovider\n");
            var coverage=new TreeMap<String,Set<Long>>();var providers=new TreeMap<String,Map<String,Integer>>();var types=new TreeMap<String,Map<String,Integer>>();var union=new HashSet<Long>();int profiles=0;
            for(var profile:fixture.path("profiles")){
                String id=profile.path("id").asString(),username=profile.path("username").asString();
                if(!username.matches("tq_eval_p[0-9]{2}"))throw new IllegalArgumentException("Unexpected fixture account");
                long user=Objects.requireNonNull(jdbc.queryForObject("SELECT user_id FROM users WHERE username=?",Long.class,username));
                var grades=new LinkedHashMap<Long,Integer>();
                for(var judgment:profile.path("judgments")){Long item=byUrl.get(key(judgment.path("url").asString()));if(item==null)throw new IllegalStateException("Judged resource is absent");int grade=judgment.path("grade").asInt();if(grade<0||grade>3)throw new IllegalArgumentException("Grades must be 0–3");grades.put(item,grade);}
                var pool=dao.findCandidates(user).stream().filter(c->grades.containsKey(c.itemId())).toList();
                if(pool.size()!=grades.size())throw new IllegalStateException("Retrieval eligibility differs from judged pool");
                union.addAll(grades.keySet());profiles++;
                var categories=new HashMap<Long,String>();pool.forEach(c->categories.put(c.itemId(),c.categoryName()));
                var algorithms=new LinkedHashMap<String,List<Long>>();
                algorithms.put("popularity",pool.stream().sorted(Comparator.comparingInt(RecommendationCandidate::popularityEvidenceCount).reversed().thenComparingLong(RecommendationCandidate::itemId)).map(RecommendationCandidate::itemId).limit(10).toList());
                var words=new ArrayList<Pattern>();for(var term:profile.path("topic").asString().split("\\s+"))words.add(Pattern.compile("\\b"+Pattern.quote(term)+"\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE));
                var keywordScores=new HashMap<Long,Long>();for(var candidate:pool){var row=metadata.get(candidate.itemId());String text=cell(row.get("title"))+" "+cell(row.get("description"));keywordScores.put(candidate.itemId(),words.stream().filter(p->p.matcher(text).find()).count());}
                algorithms.put("keyword",pool.stream().sorted(Comparator.comparingLong((RecommendationCandidate c)->keywordScores.get(c.itemId())).reversed().thenComparingLong(RecommendationCandidate::itemId)).map(RecommendationCandidate::itemId).limit(10).toList());
                algorithms.put("content",pool.stream().sorted(Comparator.comparingDouble((RecommendationCandidate c)->c.contentSimilarity()==null?0:c.contentSimilarity().doubleValue()).reversed().thenComparingLong(RecommendationCandidate::itemId)).map(RecommendationCandidate::itemId).limit(10).toList());
                int exploration=Objects.requireNonNull(jdbc.queryForObject("SELECT exploration FROM users WHERE user_id=?",Integer.class,user));
                algorithms.put("hybrid",ranker.recommend(pool,10,exploration).stream().map(RecommendationDraft::itemId).toList());
                for(var algorithm:algorithms.entrySet()){
                    var ranked=algorithm.getValue();for(int i=0;i<ranked.size();i++){long item=ranked.get(i);var row=metadata.get(item);rankings.append(id).append('\t').append(algorithm.getKey()).append('\t').append(i+1).append('\t').append(cell(row.get("external_url"))).append('\t').append(cell(row.get("title"))).append('\t').append(grades.get(item)).append('\t').append(cell(row.get("item_type"))).append('\t').append(cell(row.get("source_name"))).append('\n');}
                    for(int k:List.of(5,10)){
                        var m=metrics(ranked,grades,categories,k);String group=algorithm.getKey()+":"+k;
                        coverage.computeIfAbsent(group,s->new HashSet<>()).addAll(ranked.stream().limit(k).toList());
                        for(long item:ranked.stream().limit(k).toList()){var row=metadata.get(item);providers.computeIfAbsent(group,s->new TreeMap<>()).merge(cell(row.get("source_name")),1,Integer::sum);types.computeIfAbsent(group,s->new TreeMap<>()).merge(cell(row.get("item_type")),1,Integer::sum);}
                        results.append(id).append(',').append(algorithm.getKey()).append(',').append(k).append(',').append(grades.size()).append(',').append(grades.values().stream().filter(g->g>0).count()).append(',').append(number(m.precision())).append(',').append(number(m.recall())).append(',').append(number(m.ndcg())).append(',').append(number(m.mrr())).append(',').append(number(m.categoryDiversity())).append('\n');
                    }
                }
            }
            if(profiles!=20||union.size()<100)throw new IllegalStateException("Insufficient evaluation scope");
            var exposure=new TreeMap<String,Object>();coverage.forEach((group,items)->exposure.put(group,Map.of("distinctResources",items.size(),"judgedPoolCoverage",(double)items.size()/union.size(),"catalogExposure",(double)items.size()/metadata.size())));
            var manifest=new LinkedHashMap<String,Object>();manifest.put("profiles",profiles);manifest.put("catalogResources",metadata.size());manifest.put("distinctJudgedResources",union.size());manifest.put("interactionRows",jdbc.queryForObject("SELECT COUNT(*) FROM interactions",Long.class));manifest.put("inputSha256",com.guvi.recommendationsystem.catalog.RealDataAcquisition.sha(input));manifest.put("feedMix",ranker.publicMix());manifest.put("exposure",exposure);manifest.put("providers",providers);manifest.put("resourceTypes",types);manifest.put("scope","Actual production DAO and ranker; graded editorial metadata proxies in common judged pools. No independent human or learning-outcome validation. No tuning. Knowledge-level context does not create a production level-adaptation feature.");
            Files.writeString(output.resolve("recommendation-results.csv"),results);Files.writeString(output.resolve("recommendation-rankings.tsv"),rankings);Files.writeString(output.resolve("recommendation-manifest.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
            System.out.println("CURRENT_CATALOG_EVALUATION profiles="+profiles+" catalog="+metadata.size()+" judged="+union.size());
        }
    }
}
