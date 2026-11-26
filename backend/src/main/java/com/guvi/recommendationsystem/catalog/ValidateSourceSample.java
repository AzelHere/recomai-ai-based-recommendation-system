package com.guvi.recommendationsystem.catalog;

import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.time.Instant;

/** Separately invoked live verification; no external website is required by automated tests. */
public final class ValidateSourceSample {
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: ValidateSourceSample <data directory>");
        var data=Path.of(args[0]);var json=JsonMapper.builder().build();var resources=json.readValue(Files.readString(data.resolve("processed/educational-resources.json")),EducationalResource[].class);
        var selected=new LinkedHashMap<String,EducationalResource>();
        for(String source:List.of("mit-ocw","medlineplus"))Arrays.stream(resources).filter(r->r.sourceId().equals(source)).limit(10).forEach(r->selected.put(r.canonicalUrl(),r));
        Arrays.stream(resources).filter(r->r.title().equals("Introduction to Programming in Java")||r.title().equals("Java Preparation for 6.170")).forEach(r->selected.put(r.canonicalUrl(),r));
        var rows=new ArrayList<Map<String,Object>>();var http=new SourceHttp();
        for(var r:selected.values()){
            String status="TEMPORARILY_UNAVAILABLE",destination=r.canonicalUrl();int code=0;
            try{var result=http.request(r.canonicalUrl(),true,0);code=result.status();destination=result.destination().toString();status=CatalogMaintenance.urlStatus(code,!destination.equals(r.canonicalUrl()));}catch(IllegalArgumentException ex){status="UNSAFE_DESTINATION";}catch(Exception ex){/* Retain uncertainty, not a false broken-link claim. */}
            rows.add(Map.of("sourceId",r.sourceId(),"externalId",r.externalId(),"url",r.canonicalUrl(),"finalUrl",destination,"httpStatus",code,"urlStatus",status,"checkedAt",Instant.now().toString()));
        }
        Files.writeString(data.resolve("manifests/live-url-sample.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
        System.out.println("Checked "+rows.size()+" real official URLs separately from deterministic software tests.");
    }
}
