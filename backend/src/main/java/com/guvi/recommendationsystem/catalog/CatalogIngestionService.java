package com.guvi.recommendationsystem.catalog;

import com.guvi.recommendationsystem.exception.InvalidUserInputException;
import com.guvi.recommendationsystem.model.*;
import com.guvi.recommendationsystem.service.ItemService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/** Reviews licensed metadata before importing it through the existing item service.
 * Preview is read-only; commit revalidates the digest and applies each batch transactionally.
 * Exact source identities are idempotent; ambiguous editions are held for review. */
@Service
public class CatalogIngestionService {
    private final JdbcTemplate jdbc;
    private final ItemService items;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher events;
    private final JsonMapper json=JsonMapper.builder().build();
    public CatalogIngestionService(JdbcTemplate jdbc,ItemService items){this.jdbc=jdbc;this.items=items;}
    public record Row(int number,String title,String decision,String reason) { }
    public record Preview(String sha256,int imported,int existing,int review,boolean canImport,List<Row> rows) {
    public Preview {
        rows = rows == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(rows));
    }
 }
    public record ImportResult(long batchId,int imported,int skipped,int review,boolean repeated) { }

    public List<Map<String,Object>> sources(){return jdbc.queryForList("SELECT * FROM catalog_sources ORDER BY source_id");}
    public List<Map<String,Object>> history(){return jdbc.queryForList("SELECT * FROM catalog_import_batches ORDER BY batch_id DESC LIMIT 30");}
    public List<Map<String,Object>> reviews(){return jdbc.queryForList("SELECT * FROM catalog_import_reviews ORDER BY last_seen_at DESC,source_id,external_id LIMIT 100");}
    public List<Map<String,Object>> stats(){return jdbc.queryForList("SELECT s.source_id,s.name,s.license,COUNT(p.item_id) resources,SUM(p.url_status IN ('ACCESSIBLE','REDIRECTED')) verified FROM catalog_sources s LEFT JOIN resource_provenance p ON p.source_id=s.source_id GROUP BY s.source_id,s.name,s.license ORDER BY s.source_id");}
    public Map<String,Object> provenance(long id){return jdbc.queryForList("SELECT p.*,s.name source_name,s.license_url FROM resource_provenance p JOIN catalog_sources s ON s.source_id=p.source_id WHERE p.item_id=?",id).stream().findFirst().orElse(Map.of());}
    /** Stored, time-bound HTTP observations; restrictions and unknowns are not evidence of a broken resource. */
    public List<Map<String,Object>> linkHealth(){
        return jdbc.queryForList("SELECT source_id,COALESCE(url_status,'NOT_CHECKED') AS status,COUNT(*) AS resources FROM resource_provenance GROUP BY source_id,url_status ORDER BY source_id,url_status");
    }

    public Preview preview(List<EducationalResource> input) {
        if(input==null || input.isEmpty() || input.size()>500)throw new InvalidUserInputException("Supply 1–500 source records per batch.");
        if(json.writeValueAsBytes(input).length>3_000_000)throw new InvalidUserInputException("Source batch exceeds 3 MB.");
        var sourceMap=new HashMap<String,Map<String,Object>>();sources().forEach(s->sourceMap.put((String)s.get("source_id"),s));
        var existingTitles=new HashSet<String>();
        jdbc.queryForList("SELECT title,source_name FROM items WHERE source_name IN (SELECT name FROM catalog_sources)")
                .forEach(row->existingTitles.add(row.get("source_name")+":"+EducationalResource.titleKey((String)row.get("title"))));
        var rows=new ArrayList<Row>();var urls=new HashSet<String>();var ids=new HashSet<String>();var titles=new HashSet<String>();
        int imports=0,existing=0,review=0;boolean valid=true;
        for(int index=0;index<input.size();index++) {
            var r=input.get(index);
            if(r==null){valid=false;rows.add(new Row(index+1,"","REJECT","Source record is required."));continue;}
            String title=r.title();
            try {
                validate(r,sourceMap);
                String canonical=EducationalResource.canonical(r.canonicalUrl()),key=r.sourceId()+":"+r.externalId();
                String decision="IMPORT",reason="New reviewed-source record";
                if(!urls.add(canonical)||!ids.add(key)) {decision="EXISTING";reason="Duplicate within batch";}
                else if(jdbc.queryForObject("SELECT COUNT(*) FROM resource_provenance WHERE (source_id=? AND external_id=?) OR canonical_sha256=?",Integer.class,r.sourceId(),r.externalId(),digest(canonical))>0
                        || jdbc.queryForObject("SELECT COUNT(*) FROM items WHERE TRIM(TRAILING '/' FROM external_url)=?",Integer.class,canonical)>0){decision="EXISTING";reason="Stable existing record; all manual metadata preserved";}
                else if(!titles.add(r.sourceId()+":"+EducationalResource.titleKey(r.title())) || existingTitles.contains(sourceMap.get(r.sourceId()).get("name")+":"+EducationalResource.titleKey(r.title()))){decision="REVIEW";reason="Same normalized title/provider, different identifier: edition or near duplicate needs review";}
                switch(decision){case "IMPORT"->imports++;case "EXISTING"->existing++;default->review++;}
                rows.add(new Row(index+1,title,decision,reason));
            }catch(RuntimeException ex){valid=false;rows.add(new Row(index+1,title,"REJECT",ex.getMessage()));}
        }
        return new Preview(RealDataAcquisition.sha(json.writeValueAsBytes(input)),imports,existing,review,valid,List.copyOf(rows));
    }
    private void validate(EducationalResource r,Map<String,Map<String,Object>> sources) {
        if(r==null||!sources.containsKey(r.sourceId()))throw new InvalidUserInputException("Source is not approved.");
        required(r.categoryCode(),80,"Category code");required(r.resourceType(),40,"Resource type");
        required(r.externalId(),160,"Source identifier");required(r.title(),150,"Title");
        if(!Objects.equals(r.title(),EducationalResource.text(r.title())))throw new InvalidUserInputException("Title must be normalized plain text.");
        if(r.description()!=null && (r.description().length()>60000 || !r.description().equals(EducationalResource.text(r.description()))))throw new InvalidUserInputException("Description must be normalized plain text, at most 60000 characters.");
        String url=EducationalResource.canonical(r.canonicalUrl());
        if(r.sourceId().equals("mit-ocw")&&!url.startsWith("https://ocw.mit.edu/courses/"))throw new InvalidUserInputException("MIT resources must link to official OCW courses.");
        if(r.sourceId().equals("medlineplus")&&(!url.matches("https://medlineplus.gov/[a-z0-9]+\\.html")||!r.categoryCode().equals("HEALTH_FITNESS")))throw new InvalidUserInputException("Only MedlinePlus topic pages are supported.");
        if(Set.of("openstax-readings","openstax-readings-ccby").contains(r.sourceId())&&!url.matches("https://openstax.org/books/[a-z0-9-]+/pages/[a-z0-9-]+"))throw new InvalidUserInputException("OpenStax readings must link to an official book section.");
        if(r.sourceId().equals("medline-genetics")&&(!url.matches("https://medlineplus.gov/genetics/understanding/[a-z0-9-]+/[a-z0-9-]+")||!r.categoryCode().equals("HEALTH_FITNESS")))throw new InvalidUserInputException("Only MedlinePlus Genetics educational articles are supported.");
        if(r.sourceId().equals("microsoft-learn")&&(!(url.startsWith("https://learn.microsoft.com/en-us/azure/developer/java/fundamentals/")||url.equals("https://learn.microsoft.com/en-us/azure/azure-sql/azure-sql-iaas-vs-paas-what-is-overview"))||!r.categoryCode().equals("TECHNOLOGY")))throw new InvalidUserInputException("Microsoft readings must link to reviewed Java fundamentals or SQL overview articles.");
        if(!Objects.equals(r.license(),sources.get(r.sourceId()).get("license")))throw new InvalidUserInputException("License must match the reviewed source.");
        required(r.attribution(),5000,"Attribution");required(r.metadataOrigin(),2000,"Metadata origins");
        if(r.language()!=null&&!r.language().matches("[a-z]{2}(-[A-Za-z]{2})?"))throw new InvalidUserInputException("Language must be a short language code or null.");
        if(r.topics()==null || r.topics().size()>40 || r.topics().stream().anyMatch(t->t==null||t.isBlank()||t.length()>150||!t.equals(EducationalResource.text(t))))throw new InvalidUserInputException("Supply at most 40 normalized topics.");
        if(!Set.of("COURSE","ARTICLE","GUIDE","DOCUMENTATION","RESOURCE").contains(r.resourceType()))throw new InvalidUserInputException("Unsupported educational resource type.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE niche_code=? AND is_active=TRUE",Integer.class,r.categoryCode())!=1)throw new InvalidUserInputException("Unknown or inactive category.");
    }
    private void required(String value,int max,String label){if(value==null||value.isBlank()||value.length()>max||value.contains("\uFFFD"))throw new InvalidUserInputException(label+" is missing or invalid.");}
    private String digest(String value){return RealDataAcquisition.sha(value.getBytes(StandardCharsets.UTF_8));}

    @Transactional
    public ImportResult commit(List<EducationalResource> input,String expectedSha256) {
        // All importers lock the same small source registry in deterministic order, including repeated/simultaneous jobs.
        jdbc.queryForList("SELECT source_id FROM catalog_sources ORDER BY source_id FOR UPDATE");
        Preview preview=preview(input);
        if(!preview.canImport() || !Objects.equals(preview.sha256(),expectedSha256))throw new InvalidUserInputException("Import was not approved for this exact valid preview. Preview again.");
        var old=jdbc.queryForList("SELECT batch_id,imported_count,skipped_count,review_count FROM catalog_import_batches WHERE dataset_sha256=?",preview.sha256());
        if(!old.isEmpty()){var row=old.getFirst();return new ImportResult(((Number)row.get("batch_id")).longValue(),0,input.size()-preview.review(),preview.review(),true);}
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{return com.guvi.recommendationsystem.dao.JdbcStatements.generated(c, "INSERT INTO catalog_import_batches(dataset_sha256,imported_count,skipped_count,review_count) VALUES(?,?,?,?)", s -> {s.setString(1,preview.sha256());s.setInt(2,preview.imported());s.setInt(3,preview.existing());s.setInt(4,preview.review());});},key);
        long batch=Objects.requireNonNull(key.getKey()).longValue();
        var sourceMap=new HashMap<String,String>();sources().forEach(s->sourceMap.put((String)s.get("source_id"),(String)s.get("name")));
        var batchTitles=new HashSet<String>();int imported=0,skipped=0,review=0;
        for(var row:preview.rows()) {
            var r=input.get(row.number()-1);String canonical=EducationalResource.canonical(r.canonicalUrl());
            boolean sameTitle=!batchTitles.add(r.sourceId()+":"+r.title().toLowerCase(Locale.ROOT));
            if(row.decision().equals("REVIEW") || row.decision().equals("IMPORT")&&sameTitle) {
                jdbc.update("INSERT INTO catalog_import_reviews(source_id,external_id,title,canonical_url,reason) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE last_seen_at=CURRENT_TIMESTAMP",r.sourceId(),r.externalId(),r.title(),canonical,"Same title/provider: review editions before merging");review++;continue;
            }
            if(!row.decision().equals("IMPORT")){skipped++;continue;}
            long category=jdbc.queryForObject("SELECT category_id FROM categories WHERE niche_code=?",Long.class,r.categoryCode());
            var item=items.create(new ItemRequest(category,r.title(),r.description(),ItemType.valueOf(r.resourceType()),canonical,null,sourceMap.get(r.sourceId()),ItemDifficulty.UNKNOWN,null,new BigDecimal("0.5000"),false));
            jdbc.update("INSERT INTO resource_provenance(item_id,source_id,external_id,canonical_url,canonical_sha256,language,license,attribution,metadata_origin,source_topics,batch_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",item.itemId(),r.sourceId(),r.externalId(),canonical,digest(canonical),r.language(),r.license(),r.attribution(),r.metadataOrigin()+"; controlled topic aliases:derived from title/description and official subject context; quality=0.5: neutral internal prior, not a rating",json.writeValueAsString(r.topics()),batch);
            // Only exact normalized official topics map to existing controlled interests; no invented keyword labels.
            var derivedTopics=new ArrayList<>(r.topics());
            if(r.categoryCode().equals("TECHNOLOGY")&&r.topics().contains("Computer Science")){
                if(r.title().matches("(?is).*\\bJava\\b.*"))derivedTopics.add("Java");
                if((r.description()==null?"":r.description()).matches("(?is).*\\bSQL\\b.*"))derivedTopics.add("SQL");
                if(r.topics().contains("Artificial Intelligence")&&(r.title()+" "+r.description()).toLowerCase(Locale.ROOT).contains("machine learning"))derivedTopics.add("Machine Learning");
            }
            for(String topic:new LinkedHashSet<>(derivedTopics))jdbc.update("INSERT IGNORE INTO item_interests(item_id,interest_id,relevance_weight) SELECT ?,interest_id,1 FROM interests WHERE LOWER(name)=LOWER(?) AND category_id=? AND is_active=TRUE",item.itemId(),topic,category);
            imported++;
        }
        jdbc.update("UPDATE catalog_import_batches SET imported_count=?,skipped_count=?,review_count=? WHERE batch_id=?",imported,skipped,review,batch);
        jdbc.update("UPDATE catalog_sources SET last_import_at=CURRENT_TIMESTAMP WHERE source_id IN (SELECT source_id FROM resource_provenance WHERE batch_id=?)",batch);
        events.publishEvent(new CatalogImported(batch));
        return new ImportResult(batch,imported,skipped,review,false);
    }
}
