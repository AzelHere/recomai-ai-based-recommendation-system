package com.guvi.recommendationsystem.recommendation;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Small-catalog TF-IDF cosine scorer over real item metadata and a user's positive item history. */
public class ContentFeatureScorer {
    private record Index(List<ItemDocument> documents, Map<String,Integer> frequencies, Map<Long,Map<String,Double>> vectors) { }
    private volatile Index index=new Index(List.of(),Map.of(),Map.of());
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static final Set<String> STOP_WORDS = Set.of("and", "the", "for", "with", "from", "into", "your",
            "this", "that", "you", "are", "how", "all", "our", "can", "will", "not", "use", "using");

    public Map<Long, Match> score(List<ItemDocument> documents, Set<Long> positivelyEngagedItemIds) {
        return score(documents,positivelyEngagedItemIds,"");
    }

    /** Scores unengaged documents against normalized positive history plus explicit topic text.
     * The cached immutable corpus contains no user profiles. Empty inputs yield an empty match map;
     * zero-length vectors remain zero, avoiding division by zero. Rebuilds publish a complete snapshot.
     * @param documents unique item IDs and metadata text from the bounded active catalog
     * @param positivelyEngagedItemIds seeds omitted from the returned content matches
     * @param selectedTopics optional cold-start topic text; unknown terms retain smoothed IDF
     * @return immutable positive cosine matches with up to three explanatory overlap terms
     */
    public Map<Long, Match> score(List<ItemDocument> documents, Set<Long> positivelyEngagedItemIds,String selectedTopics) {
        if (documents == null || documents.isEmpty() || positivelyEngagedItemIds == null) return Map.of();
        if(positivelyEngagedItemIds.isEmpty() && (selectedTopics==null||selectedTopics.isBlank()))return Map.of();
        Index current=index;
        if(!current.documents().equals(documents))current=buildIndex(documents);
        var vectors=current.vectors();
        Map<String, Double> profile = new HashMap<>();
        int seedCount = 0;
        for (Long seedId : positivelyEngagedItemIds) {
            Map<String, Double> vector = vectors.get(seedId);
            if (vector == null || vector.isEmpty()) continue;
            vector.forEach((token, weight) -> profile.merge(token, weight, Double::sum));
            seedCount++;
        }
        if(seedCount>0){double count=seedCount;profile.replaceAll((token,weight)->weight/count);normalize(profile);}
        if(selectedTopics!=null&&!selectedTopics.isBlank())vector(tokens(selectedTopics),current.frequencies(),documents.size())
                .forEach((token,weight)->profile.merge(token,weight,Double::sum));
        if(profile.isEmpty())return Map.of();
        normalize(profile);

        Map<Long, Match> matches = new HashMap<>();
        for (ItemDocument document : documents) {
            if (positivelyEngagedItemIds.contains(document.itemId())) continue;
            Map<String, Double> candidate = vectors.get(document.itemId());
            double cosine = cosine(profile, candidate);
            if (cosine <= 0) continue;
            List<String> overlappingTerms = candidate.keySet().stream().filter(profile::containsKey)
                    .sorted(Comparator.comparingDouble((String token) -> profile.get(token) * candidate.get(token))
                            .reversed().thenComparing(Comparator.naturalOrder()))
                    .limit(3).toList();
            matches.put(document.itemId(), new Match(Math.min(1,cosine), String.join(", ", overlappingTerms)));
        }
        return Map.copyOf(matches);
    }

    private synchronized Index buildIndex(List<ItemDocument> documents) {
        if(index.documents().equals(documents))return index;

        Map<Long, Map<String, Integer>> tokensByItem = new LinkedHashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (ItemDocument document : documents) {
            Map<String, Integer> tokens = tokens(document.text());
            tokensByItem.put(document.itemId(), tokens);
            tokens.keySet().forEach(token -> documentFrequency.merge(token, 1, Integer::sum));
        }

        int corpusSize = Math.max(1, documents.size());
        Map<Long, Map<String, Double>> vectors = new HashMap<>();
        tokensByItem.forEach((itemId, tokens) -> vectors.put(itemId, vector(tokens, documentFrequency, corpusSize)));
        vectors.replaceAll((id,vector)->Map.copyOf(vector));
        index=new Index(List.copyOf(documents),Map.copyOf(documentFrequency),Map.copyOf(vectors));
        return index;
    }

    private Map<String, Integer> tokens(String text) {
        Map<String, Integer> result = new HashMap<>();
        if (text == null) return result;
        var matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOP_WORDS.contains(token)) result.merge(token, 1, Integer::sum);
        }
        return result;
    }

    private Map<String, Double> vector(Map<String, Integer> tokens, Map<String, Integer> df, int corpusSize) {
        Map<String, Double> result = new HashMap<>();
        tokens.forEach((token, frequency) -> {
            double tf = 1.0 + Math.log(frequency);
            double idf = 1.0 + Math.log((corpusSize + 1.0) / (df.getOrDefault(token, 0) + 1.0));
            result.put(token, tf * idf);
        });
        normalize(result);
        return result;
    }

    private void normalize(Map<String, Double> vector) {
        double norm = Math.sqrt(vector.values().stream().mapToDouble(value -> value * value).sum());
        if (norm == 0) return;
        vector.replaceAll((token, value) -> value / norm);
    }

    private double cosine(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right == null || right.isEmpty()) return 0;
        return left.entrySet().stream().mapToDouble(entry -> entry.getValue() * right.getOrDefault(entry.getKey(), 0.0))
                .sum();
    }

    public record ItemDocument(long itemId, String text) { }

    public record Match(double score, String overlappingTerms) { }
}
