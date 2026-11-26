package com.guvi.recommendationsystem.evaluation;

import com.guvi.recommendationsystem.dao.JdbcRecommendationDao;
import com.guvi.recommendationsystem.recommendation.ScoringRecommendationEngine;
import com.guvi.recommendationsystem.service.InteractionSignalWeights;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;

/** Standalone CLI. No Spring Boot startup, migration, seeding, or recommendation writes. */
public final class OfflineEvaluation {
    public static void main(String[] args) throws Exception {
        if (args.length != 6) throw new IllegalArgumentException(
                "Expected: REAL|SYNTHETIC snapshot-database port cutoff-UTC end-UTC outcomes.tsv");
        String source = args[0], database = args[1];
        if (!Set.of("REAL","SYNTHETIC").contains(source) || !database.matches("recomai_eval_[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Supply a labelled isolated evaluation snapshot.");
        int port = Integer.parseInt(args[2]);
        Instant cutoff = Instant.parse(args[3]), end = Instant.parse(args[4]);
        if (!end.isAfter(cutoff) || cutoff.getNano() != 0) throw new IllegalArgumentException("Invalid time window");
        Path file = Path.of(args[5]);
        if (Files.size(file) > 10_000_000) throw new IllegalArgumentException("Outcome file exceeds 10 MB");
        byte[] bytes = Files.readAllBytes(file);
        var events = new ArrayList<Evaluation.Event>();
        for (String line : new String(bytes, java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Invalid outcome row " + (events.size()+1));
            events.add(new Evaluation.Event(Long.parseLong(fields[0]), Long.parseLong(fields[1]),
                    Instant.parse(fields[2]), fields[3]));
        }
        var labels = Evaluation.labels(events, cutoff, end);
        String url = "jdbc:mysql://127.0.0.1:" + port + "/" + database + "?connectionTimeZone=UTC";
        try (Connection conn = DriverManager.getConnection(url, System.getenv("EVAL_USER"), System.getenv("EVAL_PASSWORD"))) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(conn, true));
            jdbc.execute("SET SESSION time_zone = '+00:00'");
            jdbc.execute("SET SESSION timestamp = " + cutoff.getEpochSecond());
            jdbc.execute("SET SESSION max_execution_time = 5000");
            jdbc.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
            for (String column : List.of("interactions.occurred_at","feedback.updated_at","user_preferences.updated_at",
                    "users.updated_at","items.updated_at","user_topic_preferences.updated_at",
                    "recommendation_system_settings.updated_at")) {
                String[] part = column.split("\\.");
                if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + part[0]
                        + " WHERE " + part[1] + " > CURRENT_TIMESTAMP)", Boolean.class)))
                    throw new IllegalArgumentException("Restore a cutoff-time backup. Future rows found in " + part[0]);
            }
            var engine = new ScoringRecommendationEngine();
            Map<String,Double> shares = new HashMap<>();
            jdbc.query("SELECT setting_key, setting_value FROM recommendation_system_settings",
                    (org.springframework.jdbc.core.RowCallbackHandler) row -> shares.put(row.getString(1), row.getDouble(2)));
            engine.configureMix(shares.get("familiar"), shares.get("related"), shares.get("discovery"));
            var dao = new JdbcRecommendationDao(jdbc, new InteractionSignalWeights(.15,.50,.65,1,1.25,1,-1.25,-1.5,1.5,-.25,1.25,.12));
            var users = jdbc.queryForList("SELECT DISTINCT user_id FROM interactions ORDER BY user_id LIMIT 501", Long.class);
            if (users.size() > 500) throw new IllegalArgumentException("Prepare a bounded snapshot (maximum 500 training users)");
            var scores = new Evaluation.Metrics[2][2];
            for (var pair : scores) Arrays.setAll(pair, ignored -> new Evaluation.Metrics());
            for (long user : users) {
                if (!labels.containsKey(user)) continue;
                var ranked = Evaluation.rank(engine, dao.findCandidates(user),
                        jdbc.queryForObject("SELECT exploration FROM users WHERE user_id=?",Integer.class,user));
                for (int i=0; i<2; i++) {
                    int k = i == 0 ? 5 : 10;
                    scores[i][0].add(ranked.hybrid(), labels.get(user), ranked.categories(), k);
                    scores[i][1].add(ranked.popularity(), labels.get(user), ranked.categories(), k);
                }
            }
            System.out.printf("Source %s | Snapshot %s | Evaluated %s | Window (%s, %s]%n",source,database,Instant.now(),cutoff,end);
            System.out.printf("Accounts %d | Catalog %d | Training interactions %d | Outcome rows %d | Eligible users %d%n",
                    count(jdbc,"users"),count(jdbc,"items"),count(jdbc,"interactions"),events.size(),scores[0][0].users);
            System.out.println("hybrid-evidence-v2, default interaction weights, per-user exploration, admin shares " + new TreeMap<>(shares));
            System.out.println("Outcomes SHA-256 " + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            System.out.println("| Metric | Hybrid | Popularity |\n|---|---:|---:|");
            String[] names = {"Precision","Recall","NDCG","Coverage","Category diversity"};
            for (int i=0; i<2; i++) {
                var hybrid = scores[i][0].values();
                var baseline = scores[i][1].values();
                for (int m=0;m<names.length;m++) System.out.printf("| %s@%d | %s | %s |%n",
                        names[m],i==0?5:10,format(hybrid[m]),format(baseline[m]));
            }
            System.out.println("N/A indicates no eligible users or no measurable category pairs.");
        }
    }

    private static long count(JdbcTemplate jdbc,String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Long.class);
    }
    private static String format(double n) { return Double.isFinite(n) ? String.format(Locale.ROOT,"%.4f",n) : "N/A"; }
}
