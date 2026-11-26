# Representative implementation excerpts

These are excerpts from the inspected source. They are included for viva explanation, not a replacement source tree.

## recommendation/ContentFeatureScorer.java

```java
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

```

## recommendation/ScoringRecommendationEngine.java

```java
package com.guvi.recommendationsystem.recommendation;

import com.guvi.recommendationsystem.model.RecommendationCandidate;
import com.guvi.recommendationsystem.model.RecommendationDraft;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explainable weighted ranker; values are ranking points, not probabilities. */
@Component
public class ScoringRecommendationEngine implements RecommendationEngine {
    private static final double CATEGORY_WEIGHT = 12.0;
    private static final double INTEREST_WEIGHT = 24.0;
    private static final double RELATED_WEIGHT = 10.0;
    private static final double CONTENT_WEIGHT = 8.0;
    private static final double RATING_WEIGHT = 7.0;
    private static final double INTERACTION_WEIGHT = 16.0;
    private static final double POPULARITY_WEIGHT = 10.0;
    private static final double COLLABORATIVE_WEIGHT = 10.0;
    private static final double PURPOSE_WEIGHT = 3.0;
    private static final double EDITOR_PICK_WEIGHT = 5.0;
    private static final double NEGATIVE_PENALTY_WEIGHT = 20.0;
    private static final int MIN_COLLABORATIVE_EVIDENCE = 3;

    private volatile FeedMix feedMix = new FeedMix(0.70, 0.20, 0.10);

    @Autowired
    public void configureFromProperties(@Value("${recommendation.mix.familiar:0.70}") double familiar,
                                        @Value("${recommendation.mix.related:0.20}") double related,
                                        @Value("${recommendation.mix.discovery:0.10}") double discovery) {
        configureMix(familiar, related, discovery);
    }

    public void configureMix(double familiar, double related, double discovery) {
        double sum = familiar + related + discovery;
        if (!Double.isFinite(sum) || familiar < 0 || related < 0 || discovery < 0
                || familiar > 1 || related > 1 || discovery > 1 || Math.abs(sum - 1.0) > 0.0001) {
            throw new IllegalArgumentException("Recommendation mix shares must be between 0 and 1 and sum to 1.");
        }
        feedMix = new FeedMix(familiar, related, discovery);
    }

    public Map<String, Double> publicMix() {
        FeedMix current = feedMix;
        return Map.of("familiar", current.familiar(), "related", current.related(), "discovery", current.discovery());
    }

    @Override
    public List<RecommendationDraft> recommend(List<RecommendationCandidate> candidates, int topN) {
        return recommend(candidates, topN, feedMix);
    }

    @Override
    public List<RecommendationDraft> recommend(List<RecommendationCandidate> candidates, int topN, int exploration) {
        FeedMix base = feedMix;
        double shift = (exploration - 50) / 100.0;
        double retained = 1 - Math.abs(shift);
        return recommend(candidates, topN, new FeedMix(
                base.familiar() * retained + Math.max(0, -shift),
                base.related() * retained,
                base.discovery() * retained + Math.max(0, shift)));
    }

    public List<RecommendationDraft> recommendWithMix(List<RecommendationCandidate> candidates, int topN,
                                                       double familiar, double related, double discovery) {
        double sum = familiar + related + discovery;
        if (!Double.isFinite(sum) || familiar < 0 || related < 0 || discovery < 0
                || familiar > 1 || related > 1 || discovery > 1 || Math.abs(sum - 1.0) > 0.0001) {
            throw new IllegalArgumentException("Recommendation mix shares must be between 0 and 1 and sum to 1.");
        }
        return recommend(candidates, topN, new FeedMix(familiar, related, discovery));
    }

    private List<RecommendationDraft> recommend(List<RecommendationCandidate> candidates, int topN, FeedMix mix) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be positive.");
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, RecommendationCandidate> unique = new LinkedHashMap<>();
        candidates.stream().filter(java.util.Objects::nonNull).forEach(candidate -> unique.putIfAbsent(candidate.itemId(),candidate));
        List<ScoredCandidate> scored = unique.values().stream()
                .filter(candidate -> candidate != null && (candidate.negativeInteractionSignal() == null
                        || candidate.negativeInteractionSignal().doubleValue() < 1.25))
                .map(this::score)
                .sorted(Comparator.comparingDouble(ScoredCandidate::rawScore).reversed()
                        .thenComparingLong(value -> value.candidate().itemId()))
                .toList();

        int familiarLimit = (int) Math.round(topN * share(mix.familiar(), mix.total()));
        int relatedLimit = (int) Math.round(topN * share(mix.related(), mix.total()));
        int discoveryLimit = Math.max(0, topN - familiarLimit - relatedLimit);
        List<ScoredCandidate> selected = new ArrayList<>(topN);
        appendBucket(scored, selected, "FAMILIAR", familiarLimit);
        appendBucket(scored, selected, "RELATED", relatedLimit);
        appendBucket(scored, selected, "DISCOVERY", discoveryLimit);
        for (ScoredCandidate candidate : scored) {
            if (selected.size() >= topN) break;
            if (!selected.contains(candidate)) addWithCategoryVariety(scored, selected, candidate);
        }
        return selected.stream().limit(topN).map(ScoredCandidate::draft).toList();
    }

    private double share(double value, double total) {
        return Math.max(0, value) / Math.max(0.0001, total);
    }

    private void appendBucket(List<ScoredCandidate> source, List<ScoredCandidate> destination,
                              String bucket, int limit) {
        List<ScoredCandidate> bucketItems = source.stream().filter(value -> value.bucketType().equals(bucket))
                .filter(value -> !destination.contains(value)).toList();
        for (int i = 0; i < limit && !bucketItems.isEmpty(); i++) {
            ScoredCandidate best=bucketItems.stream().filter(value->!destination.contains(value)).findFirst().orElse(null);
            if(best==null)return;
            ScoredCandidate next=bucketItems.stream().filter(value->!destination.contains(value))
                    .filter(value->!sameCategoryAtEnd(destination,value) && comparableRelevance(best,value))
                    .findFirst().orElse(best);
            if (next == null) return;
            destination.add(next);
        }
    }

    private void addWithCategoryVariety(List<ScoredCandidate> source, List<ScoredCandidate> destination,
                                        ScoredCandidate candidate) {
        if (!sameCategoryAtEnd(destination, candidate)) {
            destination.add(candidate);
            return;
        }
        ScoredCandidate alternative = source.stream().filter(value -> !destination.contains(value))
                .filter(value -> !sameCategoryAtEnd(destination, value) && comparableRelevance(candidate,value)).findFirst().orElse(candidate);
        destination.add(alternative);
    }

    private boolean comparableRelevance(ScoredCandidate best,ScoredCandidate alternative) {
        // Diversity may break near ties, but cannot replace a strong match with an unrelated one.
        return alternative.rawScore() >= best.rawScore() - Math.max(2.0,best.rawScore()*0.10);
    }

    private boolean sameCategoryAtEnd(List<ScoredCandidate> destination, ScoredCandidate candidate) {
        if (destination.isEmpty()) return false;
        String category = candidate.candidate().categoryName();
        String previous = destination.getLast().candidate().categoryName();
        return category != null && !category.isBlank() && category.equalsIgnoreCase(previous);
    }

    private ScoredCandidate score(RecommendationCandidate candidate) {
        double category = unit(candidate.categoryPreferenceWeight());
        double interest = unit(candidate.interestMatch());
        double related = unit(candidate.relatedInterestMatch());
        double content = unit(candidate.contentSimilarity());
        double rating = ratingMatch(candidate);
        double positive = nonNegative(candidate.positiveInteractionSignal());
        double negative = nonNegative(candidate.negativeInteractionSignal());
        double positiveActivity = Math.tanh(positive / 2.0);
        double collaborative = candidate.collaborativeEvidenceCount() >= MIN_COLLABORATIVE_EVIDENCE
                ? unit(candidate.collaborativeMatch()) : 0.0;
        double popularity = candidate.popularityEvidenceCount() >= MIN_COLLABORATIVE_EVIDENCE
                ? unit(candidate.popularityMatch()) : 0.0;
        double purpose = unit(candidate.purposeMatch());
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        putPoints(breakdown, "category_preference", CATEGORY_WEIGHT * category);
        putPoints(breakdown, "selected_topic_match", INTEREST_WEIGHT * interest);
        putPoints(breakdown, "related_topic_match", RELATED_WEIGHT * related);
        putPoints(breakdown, "metadata_similarity", CONTENT_WEIGHT * content);
        putPoints(breakdown, "positive_interaction", INTERACTION_WEIGHT * positiveActivity);
        putPoints(breakdown, "rating_affinity", RATING_WEIGHT * rating);
        putPoints(breakdown, "catalog_popularity", POPULARITY_WEIGHT * popularity);
        putPoints(breakdown, "collaborative_evidence", COLLABORATIVE_WEIGHT * collaborative);
        putPoints(breakdown, "onboarding_goal", PURPOSE_WEIGHT * purpose);
        putPoints(breakdown, "editor_pick", candidate.editorPick() ? EDITOR_PICK_WEIGHT : 0.0);
        double positivePoints = breakdown.values().stream().mapToDouble(BigDecimal::doubleValue).sum();
        double penaltyPoints = Math.min(positivePoints, NEGATIVE_PENALTY_WEIGHT * Math.tanh(negative / 2.0));
        putPoints(breakdown, "negative_feedback_penalty", -penaltyPoints);
        BigDecimal stored = breakdown.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        String bucket = interest >= 0.10 || positive > 0 || rating > 0 || category > 0 && related < 0.10 ? "FAMILIAR"
                : related >= 0.10 || content >= 0.10 || category > 0 || collaborative > 0 ? "RELATED" : "DISCOVERY";
        String topic = candidate.matchedTopics() == null || candidate.matchedTopics().isBlank()
                ? "topics you have explored" : candidate.matchedTopics();
        String reasonType;
        String reason;
        if (bucket.equals("DISCOVERY")) {
            if (candidate.editorPick()) {
                reasonType = "EDITOR_PICK";
                reason = "Editor's pick: selected by the Recom AI catalog team as a useful starting resource.";
            } else if (popularity > 0) {
                reasonType = "POPULARITY";
                reason = "Popular in this catalog based on recent positive activity from multiple users.";
            } else {
                reasonType = "COLD_START";
                reason = "Exploratory pick: there is not enough preference or activity evidence to personalize this yet.";
            }
        } else if (bucket.equals("RELATED")) {
            if (collaborative > 0 && related < 0.10 && content < 0.10) {
                reasonType = "COLLABORATIVE";
                reason = "People with overlapping positive item activity also engaged with this resource.";
            } else if (content >= 0.10 && (candidate.contentMatchTerms() != null
                    && !candidate.contentMatchTerms().isBlank())) {
                reasonType = "CONTENT_SIMILARITY";
                reason = "Its description and topics overlap with your selected topics or positive resource history: "
                        + candidate.contentMatchTerms() + ".";
            } else {
                reasonType = "RELATED_INTEREST";
                reason = "Related to topics you have explored, while introducing a nearby subject.";
            }
        } else if (positive > 0) {
            reasonType = "RECENT_ACTIVITY";
            reason = "Your recent activity suggests interest in " + topic + ".";
        } else if (interest > 0) {
            reasonType = "PROFILE_MATCH";
            reason = "Recommended because it matches your topic interests: " + topic + ".";
        } else if (category > 0) {
            reasonType = "PROFILE_MATCH";
            String categoryName = candidate.categoryName() == null || candidate.categoryName().isBlank()
                    ? "a category you selected" : candidate.categoryName();
            reason = "Recommended because it matches your selected category: " + categoryName + ".";
        } else if (rating > 0) {
            reasonType = "RATING_AFFINITY";
            reason = "Its catalog rating is close to the ratings you have given in this category.";
        } else {
            reasonType = "ONBOARDING";
            reason = "A starting point based on the area you chose during onboarding.";
        }
        if (negative > 0) {
            reason += " Negative feedback has reduced its score.";
        }
        if (candidate.collaborativeEvidenceCount() > 0 && collaborative == 0) {
            reason += " Similar-user evidence is too sparse to influence this ranking.";
        }
        if (purpose > 0) {
            reason += " It also aligns with your onboarding goal.";
        }
        if (candidate.editorPick() && !reasonType.equals("EDITOR_PICK")) {
            reason += " It is also an editor-picked resource.";
        }
        return new ScoredCandidate(candidate, stored.doubleValue(), new RecommendationDraft(candidate.itemId(),
                candidate.itemTitle(), stored, reason, reasonType, bucket, breakdown), bucket);
    }

    private void putPoints(Map<String, BigDecimal> breakdown, String key, double points) {
        breakdown.put(key, BigDecimal.valueOf(points).setScale(4, RoundingMode.HALF_UP));
    }

    private double ratingMatch(RecommendationCandidate candidate) {
        BigDecimal item = candidate.itemAverageRating();
        if (item == null) return 0;
        BigDecimal personal = candidate.userAverageRating();
        if (personal == null) return 0;
        return Math.max(0, 1 - Math.abs(personal.doubleValue() - item.doubleValue()) / 4.0);
    }

    private double unit(BigDecimal value) {
        return value == null ? 0 : Math.max(0, Math.min(1, value.doubleValue()));
    }

    private double nonNegative(BigDecimal value) {
        return value == null ? 0 : Math.max(0, value.doubleValue());
    }

    private record ScoredCandidate(RecommendationCandidate candidate, double rawScore,
                                   RecommendationDraft draft, String bucketType) {
    }
    private record FeedMix(double familiar, double related, double discovery) {
        private double total() { return familiar + related + discovery; }
    }
}

```

## web/SessionAccess.java

```java
package com.guvi.recommendationsystem.web;

import com.guvi.recommendationsystem.exception.ForbiddenRequestException;
import com.guvi.recommendationsystem.exception.UnauthorizedRequestException;
import com.guvi.recommendationsystem.dao.UserDao;
import com.guvi.recommendationsystem.model.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class SessionAccess {
    private static final String USER_ID_ATTRIBUTE = "authenticatedUserId";
    private static final String USER_ROLE_ATTRIBUTE = "authenticatedUserRole";
    private static final String AUTH_VERSION_ATTRIBUTE = "authenticatedUserVersion";

    private final UserDao userDao;

    public SessionAccess(UserDao userDao) { this.userDao = userDao; }

    public void startSession(HttpServletRequest request, UserProfile profile) {
        HttpSession previousSession = request.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID_ATTRIBUTE, profile.userId());
        session.setAttribute(USER_ROLE_ATTRIBUTE, profile.role());
        session.setAttribute(AUTH_VERSION_ATTRIBUTE, userDao.findAuthVersion(profile.userId()).orElse(1));
    }

    public long requireUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new UnauthorizedRequestException("Sign in to access this resource.");
        }
        Object userId = session.getAttribute(USER_ID_ATTRIBUTE);
        if (!(userId instanceof Long authenticatedUserId)) {
            throw new UnauthorizedRequestException("Sign in to access this resource.");
        }
        Object sessionVersion = session.getAttribute(AUTH_VERSION_ATTRIBUTE);
        java.util.OptionalInt dbVersion = userDao.findAuthVersion(authenticatedUserId);
        int currentVersion = dbVersion == null ? -1 : dbVersion.orElse(-1);
        int version = sessionVersion instanceof Integer savedVersion ? savedVersion : 1;
        if (currentVersion < 0 || version != currentVersion) {
            session.invalidate();
            throw new UnauthorizedRequestException("Sign in to access this resource.");
        }
        return authenticatedUserId;
    }

    public long requireAdmin(HttpServletRequest request) {
        long userId = requireUserId(request);
        HttpSession session = request.getSession(false);
        Object role = session == null ? null : session.getAttribute(USER_ROLE_ATTRIBUTE);
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenRequestException("Administrator access is required.");
        }
        return userId;
    }

    public void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}

```

## web/SameOriginMutationFilter.java

```java
package com.guvi.recommendationsystem.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Browser CSRF defense in addition to Strict cookies and JSON-only mutation bodies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+2)
public class SameOriginMutationFilter extends OncePerRequestFilter {
    private final String publicOrigin;
    public SameOriginMutationFilter(){publicOrigin=null;}
    @org.springframework.beans.factory.annotation.Autowired
    public SameOriginMutationFilter(@org.springframework.beans.factory.annotation.Value("${app.public-url:http://localhost:8080}") String origin){publicOrigin=origin.replaceAll("/+$","");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
            FilterChain chain) throws ServletException,IOException {
        if(request.getRequestURI().startsWith("/api/") && !Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
            String origin=request.getHeader("Origin");
            boolean allowed=!"cross-site".equals(request.getHeader("Sec-Fetch-Site"));
            if(origin!=null) {
                try {
                    URI supplied=URI.create(origin),expected=URI.create(publicOrigin==null?request.getRequestURL().toString():publicOrigin);
                    allowed &= supplied.getUserInfo()==null && supplied.getQuery()==null && supplied.getFragment()==null
                            && (supplied.getRawPath()==null || supplied.getRawPath().isEmpty())
                            && expected.getScheme().equalsIgnoreCase(supplied.getScheme())
                            && expected.getHost().equalsIgnoreCase(supplied.getHost()) && port(expected)==port(supplied);
                } catch(RuntimeException ex){allowed=false;}
            }
            if(!allowed){response.setStatus(403);response.setContentType("application/json");
                response.getWriter().write("{\"status\":403,\"message\":\"Cross-origin changes are not allowed.\"}");return;}
        }
        chain.doFilter(request,response);
    }
    private static int port(URI uri){return uri.getPort()!=-1?uri.getPort():"https".equalsIgnoreCase(uri.getScheme())?443:80;}
}

```

## service/ProductionConfigurationGuard.java

```java
package com.guvi.recommendationsystem.service;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import java.net.URI;

@Component
@Profile("production")
public class ProductionConfigurationGuard implements InitializingBean {
    private final Environment environment;
    public ProductionConfigurationGuard(Environment environment){this.environment=environment;}
    @Override public void afterPropertiesSet(){
        URI origin;
        try{origin=URI.create(environment.getRequiredProperty("app.public-url"));}
        catch(RuntimeException ex){throw new IllegalStateException("Production requires an HTTPS public origin.");}
        if(!"https".equals(origin.getScheme()) || origin.getHost()==null || origin.getUserInfo()!=null
                || origin.getQuery()!=null || origin.getFragment()!=null || !(origin.getPath().isEmpty()||origin.getPath().equals("/")))
            throw new IllegalStateException("Production requires an HTTPS public origin.");
        for(String property:new String[]{"spring.datasource.password","spring.mail.username","spring.mail.password"})
            if(environment.getProperty(property,"").isBlank())throw new IllegalStateException("Production database and authenticated SMTP credentials must be configured.");
        if(!environment.getProperty("server.servlet.session.cookie.secure",Boolean.class,false))
            throw new IllegalStateException("Production requires secure session cookies.");
    }
}

```
