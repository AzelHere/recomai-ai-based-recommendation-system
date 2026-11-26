package com.guvi.recommendationsystem.evaluation;

import com.guvi.recommendationsystem.catalog.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Separate real-learner benchmark; never connects to MySQL or exports learner-level records. */
public final class OuladEvaluation {
    private static final String URL="https://archive.ics.uci.edu/static/public/349/open+university+learning+analytics+dataset.zip";
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: OuladEvaluation <data/evaluation directory>");
        Path dir=Path.of(args[0]);Files.createDirectories(dir.resolve("raw"));Path raw=dir.resolve("raw/oulad.zip");
        if(!Files.exists(raw)){var result=new SourceHttp().request(URL,false,60_000_000);if(result.status()!=200)throw new IOException("OULAD download HTTP "+result.status());Files.write(raw,result.body());}
        String sha=RealDataAcquisition.sha(Files.readAllBytes(raw));
        Map<Long,Set<Long>> train=new TreeMap<>(),test=new TreeMap<>();long rows=0,scoped=0;int selected;
        try(var zip=new ZipFile(raw.toFile())) {
            var learners=new TreeSet<Long>();
            try(var reader=reader(zip,"studentRegistration.csv")){reader.readLine();String line;while((line=reader.readLine())!=null){String[] v=line.replace("\"", "").split(",",-1);if(v[0].equals("CCC")&&v[1].equals("2014B"))learners.add(Long.valueOf(v[2]));}}
            Set<Long> sample=new HashSet<>(learners.stream().limit(1000).toList());selected=sample.size();
            try(var reader=reader(zip,"studentVle.csv")){reader.readLine();String line;while((line=reader.readLine())!=null){rows++;String[] v=line.replace("\"", "").split(",",-1);if(v.length!=6||!v[0].equals("CCC")||!v[1].equals("2014B"))continue;long user=Long.parseLong(v[2]);if(!sample.contains(user))continue;int day=Integer.parseInt(v[4]);if(day<0||day>80)continue;scoped++;long item=Long.parseLong(v[3]);(day<=40?train:test).computeIfAbsent(user,k->new HashSet<>()).add(item);}}
        }
        Map<Long,Integer> popularity=new TreeMap<>();Map<Long,Map<Long,Integer>> cooccurrence=new HashMap<>();
        for(var seen:train.values())for(long a:seen){popularity.merge(a,1,Integer::sum);for(long b:seen)if(a!=b)cooccurrence.computeIfAbsent(a,k->new HashMap<>()).merge(b,1,Integer::sum);}
        var output=new StringBuilder("dataset,algorithm,k,users,candidates,precision,recall,ndcg,mrr,hit_rate,coverage,duplicate_rate\n");
        int evaluated=0;var coverage=new HashMap<String,Set<Long>>();var sums=new HashMap<String,double[]>();
        for(var entry:train.entrySet()) {
            Set<Long> known=entry.getValue(),truth=new HashSet<>(test.getOrDefault(entry.getKey(),Set.of()));truth.removeAll(known);truth.retainAll(popularity.keySet());
            if(known.size()<3||truth.isEmpty())continue;evaluated++;
            var candidates=popularity.keySet().stream().filter(i->!known.contains(i)).toList();
            for(String algorithm:List.of("popularity","item_cosine")) {
                var scores=new HashMap<Long,Double>();
                for(long candidate:candidates){double score=algorithm.equals("popularity")?popularity.get(candidate).doubleValue():0.0;
                    if(algorithm.equals("item_cosine"))for(long seed:known)score+=cooccurrence.getOrDefault(seed,Map.of()).getOrDefault(candidate,0)/Math.sqrt((double)popularity.get(seed)*popularity.get(candidate));
                    scores.put(candidate,score);
                }
                var ranked=candidates.stream().sorted(Comparator.comparingDouble((Long i)->scores.get(i)).reversed().thenComparingLong(Long::longValue)).toList();
                for(int k:List.of(5,10)){String key=algorithm+":"+k;var top=ranked.stream().limit(k).toList();var sum=sums.computeIfAbsent(key,s->new double[5]);coverage.computeIfAbsent(key,s->new HashSet<>()).addAll(top);
                    int hits=0,first=0;double dcg=0,idcg=0;for(int i=0;i<top.size();i++)if(truth.contains(top.get(i))){hits++;dcg+=1/(Math.log(i+2)/Math.log(2));if(first==0)first=i+1;}for(int i=0;i<Math.min(k,truth.size());i++)idcg+=1/(Math.log(i+2)/Math.log(2));
                    sum[0]+=(double)hits/k;sum[1]+=(double)hits/truth.size();sum[2]+=dcg/idcg;sum[3]+=first==0?0:1.0/first;sum[4]+=hits>0?1:0;
                }
            }
        }
        if(evaluated==0)throw new IllegalStateException("No eligible real histories; do not report invented metrics.");
        for(String algorithm:List.of("popularity","item_cosine"))for(int k:List.of(5,10)){String key=algorithm+":"+k;var sum=sums.get(key);output.append("OULAD_CCC_2014B,").append(algorithm).append(',').append(k).append(',').append(evaluated).append(',').append(popularity.size());for(double value:sum)output.append(',').append(value/evaluated);output.append(',').append((double)coverage.get(key).size()/popularity.size()).append(",0\n");}
        Files.writeString(dir.resolve("oulad-results.csv"),output);
        var metadata=Map.of("source",URL,"license","CC-BY-4.0","sha256",sha,"scannedRows",rows,"scopedRows",scoped,"selectedLearners",selected,"evaluatedLearners",evaluated,"trainingItems",popularity.size(),"split","CCC/2014B; lowest 1000 anonymous numeric IDs; train days 0..40; test days 41..80; binary distinct clicks; unseen targets restricted to train-known items; all train-known unseen candidates; >=3 train resources and >=1 unseen test target; no random sampling");
        Files.writeString(dir.resolve("oulad-manifest.json"),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(metadata));
        System.out.print(output);System.out.println("Real source checksum: "+sha+"; scanned="+rows+"; selected="+selected+"; evaluated="+evaluated);
    }
    private static BufferedReader reader(ZipFile zip,String name)throws IOException {
        var entry=zip.getEntry(name);if(entry==null||entry.getSize()<0||entry.getSize()>500_000_000)throw new IOException("Missing or oversized reviewed ZIP entry: "+name);
        // Read named entries directly, never extract archive paths or execute downloaded content.
        return new BufferedReader(new InputStreamReader(zip.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8),65536);
    }
}
