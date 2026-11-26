package com.guvi.recommendationsystem.catalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Operator CLI: fixed official endpoints, bounded sequential downloads, cached raw files and auditable transformations. */
public final class RealDataAcquisition {
    private final JsonMapper json=JsonMapper.builder().build();
    private final SourceHttp http=new SourceHttp();
    private final Path data;
    private final List<Map<String,Object>> downloads=new ArrayList<>();
    private final List<Map<String,String>> rejected=new ArrayList<>();
    private final Map<String,EducationalResource> records=new LinkedHashMap<>();
    private int acquired;
    private RealDataAcquisition(Path data){this.data=data;}
    public static void main(String[] args)throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: RealDataAcquisition <data directory>");
        new RealDataAcquisition(Path.of(args[0])).run();
    }
    private byte[] download(String name,String url,int max)throws Exception {
        Path path=data.resolve("raw").resolve(name);
        // Explicit --refresh is unnecessary: remove/rename only an individual cache file when a new acquisition is wanted.
        if(Files.exists(path)) {
            byte[] bytes=Files.readAllBytes(path);
            if(bytes.length>max)throw new IllegalArgumentException("Oversized cached source.");
            downloads.add(Map.of("file","raw/"+name,"url",url,"cached",true,"bytes",bytes.length,"sha256",sha(bytes),"acquiredAt",Files.getLastModifiedTime(path).toInstant().toString()));return bytes;
        }
        Exception last=null;
        for(int retry=0;retry<3;retry++)try {
            var result=http.request(url,false,max);
            if(result.status()!=200)throw new java.io.IOException("Source returned HTTP "+result.status());
            Files.write(path,result.body());
            downloads.add(Map.of("file","raw/"+name,"url",url,"cached",false,"bytes",result.body().length,"sha256",sha(result.body()),"acquiredAt",Instant.now().toString()));return result.body();
        }catch(Exception ex){last=ex;if(retry<2)Thread.sleep(1000L<<retry);}
        throw last;
    }
    private void run()throws Exception {
        for(String sub:List.of("raw","processed","manifests","rejected","evaluation","licenses"))Files.createDirectories(data.resolve(sub));
        try {mit();}catch(Exception ex){rejected.add(Map.of("source","mit-ocw","reason","Acquisition failed: "+ex.getMessage()));}
        try {medline();}catch(Exception ex){rejected.add(Map.of("source","medlineplus","reason","Acquisition failed: "+ex.getMessage()));}
        if(records.isEmpty())throw new IllegalStateException("No genuine resources acquired; inspect source failures.");
        var list=List.copyOf(records.values());
        Files.writeString(data.resolve("processed/educational-resources.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(list));
        StringBuilder csv=new StringBuilder("source_id,external_id,title,description,canonical_url,category_code,resource_type,topics,language,license,attribution,metadata_origin\n");
        for(var r:list){var cells=Arrays.asList(r.sourceId(),r.externalId(),r.title(),r.description(),r.canonicalUrl(),r.categoryCode(),r.resourceType(),String.join("|",r.topics()),r.language(),r.license(),r.attribution(),r.metadataOrigin());csv.append(cells.stream().map(RealDataAcquisition::csv).collect(java.util.stream.Collectors.joining(","))).append('\n');}
        Files.writeString(data.resolve("processed/educational-resources.csv"),csv);
        Files.writeString(data.resolve("rejected/records.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(rejected));
        Map<String,Long> categories=new TreeMap<>(),sources=new TreeMap<>();list.forEach(r->{categories.merge(r.categoryCode(),1L,Long::sum);sources.merge(r.sourceId(),1L,Long::sum);});
        var manifest=new LinkedHashMap<String,Object>();
        manifest.put("generatedAt",Instant.now().toString());manifest.put("processingVersion","recomai-3.5-v1");manifest.put("acquiredRecords",acquired);manifest.put("validRecords",list.size());manifest.put("rejectedOrOutOfScope",rejected.size());manifest.put("categoryDistribution",categories);manifest.put("sourceDistribution",sources);manifest.put("unknownDuration",list.size());manifest.put("unknownDifficulty",list.size());manifest.put("unknownLanguage",list.stream().filter(r->r.language()==null).count());manifest.put("urlStatus","NOT_CHECKED: official feed URLs; HTTP checks are separately recorded");manifest.put("downloads",downloads);
        Files.writeString(data.resolve("manifests/acquisition-manifest.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
        StringBuilder hashes=new StringBuilder();
        for(String name:List.of("processed/educational-resources.json","processed/educational-resources.csv","manifests/acquisition-manifest.json","rejected/records.json"))hashes.append(sha(Files.readAllBytes(data.resolve(name)))).append("  ").append(name).append('\n');
        Files.writeString(data.resolve("manifests/dataset-checksums.sha256"),hashes);
        System.out.println(json.writeValueAsString(Map.of("acquired",acquired,"valid",list.size(),"categories",categories,"sources",sources,"rejectedOrOutOfScope",rejected.size())));
    }
    private void mit()throws Exception {
        int expected=1;
        for(int page=0;page<30 && page*100<expected;page++) {
            var root=json.readTree(download("mit-ocw-"+page+".json","https://api.learn.mit.edu/api/v1/courses/?platform=ocw&limit=100&offset="+page*100,8_000_000));
            expected=root.path("count").asInt();
            if(expected>3000)expected=3000;
            for(var row:root.path("results")) {
                acquired++;
                var topics=new ArrayList<String>();row.path("ocw_topics").forEach(t->topics.add(t.asString()));
                String category=category(topics),id=row.path("id").asString(),title=EducationalResource.text(row.path("title").asString());
                if(row.path("description").asString("").toLowerCase(Locale.ROOT).contains("has been retired from ocw")){reject("mit-ocw",id,"Provider explicitly identifies the course as retired");continue;}
                if(!row.path("license_cc").asBoolean()||!row.path("platform").path("code").asString().equals("ocw")||category==null) {reject("mit-ocw",id,"Outside reviewed license/platform/five-area scope");continue;}
                String authors=row.path("runs").isEmpty()?"":streamAuthors(row.path("runs").get(0).path("instructors"));
                add(new EducationalResource("mit-ocw",id,title,EducationalResource.text(row.path("description").asString(null)),row.path("url").asString(),category,"COURSE",topics,null,"CC-BY-NC-SA-4.0",authors+". "+title+". Massachusetts Institute of Technology: MIT OpenCourseWare. https://ocw.mit.edu/ — CC BY-NC-SA 4.0.","title,description,url,topics:provider; description:plain-text-converted; category:derived from official OCW subjects; difficulty,duration,language:unknown"));
            }
        }
    }
    private String streamAuthors(JsonNode nodes){var names=new ArrayList<String>();nodes.forEach(n->{String name=n.path("full_name").asString("");if(!name.isBlank())names.add(name);});return String.join(", ",names);}
    static String category(List<String> topics) {
        Set<String> t=new HashSet<>(topics);
        // Specific technical and study subjects take precedence over broad cross-listings such as Business or Fine Arts.
        if(t.contains("Computer Science"))return "TECHNOLOGY";
        if(t.stream().anyMatch(Set.of("Teaching and Education","Communication","Academic Writing","Educational Technology","Higher Education")::contains))return "LEARNING_CAREER";
        if(t.stream().anyMatch(Set.of("Business","Management","Economics","Finance","Accounting","Entrepreneurship","Marketing")::contains))return "BUSINESS_FINANCE";
        if(t.stream().anyMatch(Set.of("Fine Arts","Literature","Music","Architecture","Writing","Media Studies","Visual Arts","Performing Arts","History","Art History")::contains))return "CREATIVE_LIFESTYLE";
        if(t.stream().anyMatch(Set.of("Teaching and Education","Communication","Academic Writing","Educational Technology","Higher Education")::contains))return "LEARNING_CAREER";
        if(t.stream().anyMatch(Set.of("Health and Medicine","Health Sciences and Technology","Physical Education and Recreation","Nutrition","Public Health")::contains))return "HEALTH_FITNESS";
        if(t.stream().anyMatch(Set.of("Computer Science","Electrical Engineering","Software Design and Engineering","Programming Languages","Information Technology","Computer Networks")::contains))return "TECHNOLOGY";
        return null;
    }
    private void medline()throws Exception {
        String page=new String(download("medline-downloads.html","https://medlineplus.gov/xml.html",500_000),StandardCharsets.UTF_8);
        var match=Pattern.compile("href=\"([^\"]*mplus_topics_[0-9-]+\\.xml)\"").matcher(page);
        if(!match.find())throw new IllegalStateException("No official XML download link found");
        String url=java.net.URI.create("https://medlineplus.gov/xml.html").resolve(match.group(1)).toString();
        byte[] xml=download("medline-topics.xml",url,40_000_000);
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var root=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));var nodes=root.getElementsByTagName("health-topic");
        int accepted=0;
        for(int i=0;i<nodes.getLength();i++) {
            var row=(Element)nodes.item(i);if(!row.getAttribute("language").equals("English"))continue;
            acquired++;
            // General educational topic summaries only, never licensed encyclopedia/drug articles or images.
            if(accepted>=200){reject("medlineplus",row.getAttribute("id"),"Bounded 200-topic sample in official feed order");continue;}
            var summary=row.getElementsByTagName("full-summary");
            var groups=row.getElementsByTagName("group");var topics=new ArrayList<String>();for(int n=0;n<groups.getLength();n++)topics.add(EducationalResource.text(groups.item(n).getTextContent()));
            add(new EducationalResource("medlineplus",row.getAttribute("id"),row.getAttribute("title"),summary.getLength()==0?null:EducationalResource.text(summary.item(0).getTextContent()),row.getAttribute("url"),"HEALTH_FITNESS","GUIDE",topics,"en","US-GOV-PUBLIC-DOMAIN","Source: MedlinePlus, National Library of Medicine. General health education; not personalized medical advice.","title,url,description,language,topics:provider; description:plain-text-converted; category:derived; difficulty,duration:unknown"));accepted++;
        }
    }
    private void add(EducationalResource record) {
        try {
            String canonical=EducationalResource.canonical(record.canonicalUrl());
            if(record.title()==null||record.title().isBlank()||record.title().length()>150||record.title().contains("\uFFFD")||(record.description()!=null&&record.description().length()>60000))throw new IllegalArgumentException("Missing/oversized/invalid-encoding metadata");
            if(records.containsKey(canonical)){reject(record.sourceId(),record.externalId(),"Duplicate canonical URL");return;}
            records.put(canonical,new EducationalResource(record.sourceId(),record.externalId(),record.title(),record.description(),canonical,record.categoryCode(),record.resourceType(),record.topics().stream().map(EducationalResource::text).distinct().toList(),record.language(),record.license(),record.attribution(),record.metadataOrigin()));
        }catch(RuntimeException ex){reject(record.sourceId(),record.externalId(),"Invalid record: "+ex.getMessage());}
    }
    private void reject(String source,String id,String reason){rejected.add(Map.of("source",source,"externalId",id,"reason",reason));}
    public static String sha(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static String csv(String value){if(value==null)return "";if(value.matches("^[=+@\\-].*"))value="'"+value;return "\""+value.replace("\"","\"\"")+"\"";}
}
