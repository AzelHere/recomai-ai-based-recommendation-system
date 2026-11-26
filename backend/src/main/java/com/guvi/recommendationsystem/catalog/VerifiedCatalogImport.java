package com.guvi.recommendationsystem.catalog;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;

/** Explicit local operator command. Never runs on ordinary application startup. Same service/transactions as Admin. */
public final class VerifiedCatalogImport {
    public static void main(String[] args)throws Exception {
        if(args.length<2||args.length>3||!Set.of("preview","apply").contains(args[0]))throw new IllegalArgumentException("Usage: VerifiedCatalogImport preview|apply <reviewed JSON file> [expected file SHA-256 for apply]");
        Path path=Path.of(args[1]);if(Files.size(path)>10_000_000)throw new IllegalArgumentException("Reviewed source file exceeds 10 MB.");
        byte[] bytes=Files.readAllBytes(path);String sha=RealDataAcquisition.sha(bytes);
        if(args[0].equals("apply")&&(args.length!=3||!sha.equals(args[2])))throw new IllegalArgumentException("Apply requires the exact previewed source-file checksum.");
        var resources=Arrays.asList(JsonMapper.builder().build().readValue(bytes,EducationalResource[].class));if(resources.isEmpty()||resources.size()>3000)throw new IllegalArgumentException("Expected 1–3000 resources.");
        try(var context=new SpringApplicationBuilder(com.guvi.recommendationsystem.RecommendationSystemApplication.class).web(WebApplicationType.NONE).run()){
            var service=context.getBean(CatalogIngestionService.class);int imported=0,existing=0,review=0;
            // Validate the complete file before the first write; each approved batch then commits atomically.
            for(int offset=0;offset<resources.size();offset+=250){var batch=resources.subList(offset,Math.min(offset+250,resources.size()));var preview=service.preview(batch);if(!preview.canImport())throw new IllegalArgumentException("Rejected rows: "+preview.rows().stream().filter(r->r.decision().equals("REJECT")).toList());}
            for(int offset=0;offset<resources.size();offset+=250){var batch=resources.subList(offset,Math.min(offset+250,resources.size()));var preview=service.preview(batch);
                if(args[0].equals("apply")){var result=service.commit(batch,preview.sha256());imported+=result.imported();existing+=result.skipped();review+=result.review();}
                else {imported+=preview.imported();existing+=preview.existing();review+=preview.review();}
            }
            System.out.println("REVIEWED_CATALOG "+args[0]+" fileSha256="+sha+" new="+imported+" existing="+existing+" review="+review+" resources="+resources.size());
        }
    }
}
