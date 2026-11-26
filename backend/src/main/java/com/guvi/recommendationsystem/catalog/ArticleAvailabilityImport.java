package com.guvi.recommendationsystem.catalog;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Explicit operator replay of the acquisition's actual availability receipts, only for the reviewed new dataset. */
public final class ArticleAvailabilityImport {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: ArticleAvailabilityImport <dataset> <acquisition manifest>");
        var json=JsonMapper.builder().build();
        if(Files.size(Path.of(args[0]))>10_000_000||Files.size(Path.of(args[1]))>5_000_000)throw new IllegalArgumentException("Oversized evidence file");
        var records=json.readValue(Files.readString(Path.of(args[0])),EducationalResource[].class);
        var receipts=new HashMap<String,tools.jackson.databind.JsonNode>();
        json.readTree(Files.readString(Path.of(args[1]))).path("downloads").forEach(r->receipts.put(r.path("url").asString(),r));
        try(var context=new SpringApplicationBuilder(com.guvi.recommendationsystem.RecommendationSystemApplication.class).web(WebApplicationType.NONE).run()) {
            var jdbc=context.getBean(JdbcTemplate.class);
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
                for(var r:records) {
                    if(!Set.of("openstax-readings","openstax-readings-ccby","medline-genetics","microsoft-learn").contains(r.sourceId()))throw new IllegalArgumentException("Not an article acquisition source");
                    var receipt=receipts.get(r.canonicalUrl());
                    if(receipt==null||!Set.of("ACCESSIBLE","REDIRECTED").contains(receipt.path("status").asString()))throw new IllegalArgumentException("No successful acquisition receipt: "+r.canonicalUrl());
                    String canonical=EducationalResource.canonical(r.canonicalUrl());
                    String destination=receipt.path("finalUrl").asString(r.canonicalUrl());SourceHttp.safeUri(destination);
                    int changed=jdbc.update("UPDATE resource_provenance SET url_status=?,url_checked_at=?,final_url=? WHERE source_id=? AND external_id=? AND canonical_sha256=?",receipt.path("status").asString(),Timestamp.from(Instant.parse(receipt.path("checkedAt").asString())),destination,r.sourceId(),r.externalId(),RealDataAcquisition.sha(canonical.getBytes(StandardCharsets.UTF_8)));
                    if(changed!=1)throw new IllegalStateException("Expected one verified imported resource");
                }
            });
            System.out.println("ARTICLE_AVAILABILITY recorded="+records.length);
        }
    }
}
