package com.guvi.recommendationsystem.catalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Explicit operator job, never a startup task. Sequential cached metadata reads use the shared SSRF-safe client. */
public final class ArticleAcquisition {
    private final Path data;
    private final SourceHttp http = new SourceHttp();
    private final JsonMapper json = JsonMapper.builder().build();
    private final List<EducationalResource> records = new ArrayList<>();
    private final List<Map<String,Object>> evidence = new ArrayList<>();
    private final Set<String> urls = new HashSet<>(), titles = new HashSet<>();
    private ArticleAcquisition(Path data) { this.data=data; }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Usage: ArticleAcquisition <data directory>");
        new ArticleAcquisition(Path.of(args[0])).run();
    }
    private String download(String url) throws Exception {
        String key=RealDataAcquisition.sha(url.getBytes(StandardCharsets.UTF_8));
        Path cache=data.resolve("raw/articles/"+key+".html");
        Path receipt=data.resolve("raw/articles/"+key+".json");
        if(Files.exists(cache)) {
            if(Files.size(cache)>4_000_000)throw new IllegalStateException("Oversized cache");
            String body=Files.readString(cache);
            String hash=RealDataAcquisition.sha(body.getBytes(StandardCharsets.UTF_8));
            if(Files.exists(receipt)) {
                var saved=json.readTree(Files.readString(receipt));
                if(!saved.path("sha256").asString().equals(hash)||!saved.path("url").asString().equals(url))throw new IllegalStateException("Cached evidence mismatch");
                evidence.add(json.readValue(Files.readString(receipt),Map.class));
            } else {
                // Legacy cache without a receipt needs an actual availability request, not a guessed final URL/status.
                var checked=http.request(url,true,0);
                var saved=Map.<String,Object>of("url",url,"cached",true,"sha256",hash,"httpStatus",checked.status(),"finalUrl",checked.destination().toString(),"checkedAt",Instant.now().toString(),"status",CatalogMaintenance.urlStatus(checked.status(),!url.equals(checked.destination().toString())));
                Files.writeString(receipt,json.writeValueAsString(saved));evidence.add(saved);
            }
            return body;
        }
        var result=http.request(url,false,4_000_000);
        var saved=Map.<String,Object>of("url",url,"cached",false,"sha256",RealDataAcquisition.sha(result.body()),"httpStatus",result.status(),"finalUrl",result.destination().toString(),"status",CatalogMaintenance.urlStatus(result.status(),!url.equals(result.destination().toString())),"checkedAt",Instant.now().toString());
        evidence.add(saved);
        if(result.status()!=200)throw new java.io.IOException("Source HTTP "+result.status()+": "+url);
        String body=new String(result.body(),StandardCharsets.UTF_8);Files.writeString(cache,body);Files.writeString(receipt,json.writeValueAsString(saved));return body;
    }
    private void run() throws Exception {
        Files.createDirectories(data.resolve("raw/articles"));Files.createDirectories(data.resolve("processed"));Files.createDirectories(data.resolve("manifests"));
        // Policy files are cached alongside the actual metadata; no challenge or denied path is bypassed.
        download("https://openstax.org/robots.txt");download("https://medlineplus.gov/robots.txt");
        download("https://raw.githubusercontent.com/MicrosoftDocs/azure-dev-docs/main/LICENSE");
        javaArticles();
        book("introduction-computer-science","TECHNOLOGY",95,List.of("Computer Science"));
        book("introduction-python-programming","TECHNOLOGY",150-count("TECHNOLOGY"),List.of("Python","Computer Science"));
        book("principles-finance","BUSINESS_FINANCE",60,List.of("Finance"));
        book("introduction-business","BUSINESS_FINANCE",100-count("BUSINESS_FINANCE"),List.of("Business"));
        book("college-success","LEARNING_CAREER",65,List.of("Study Skills","Career Development"));
        book("organizational-behavior","LEARNING_CAREER",100-count("LEARNING_CAREER"),List.of("Leadership","Communication"));
        book("writing-guide","CREATIVE_LIFESTYLE",50,List.of("Writing","Creativity"));
        genetics();
        Files.writeString(data.resolve("processed/educational-articles.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(records));
        var categories=new TreeMap<String,Long>();records.forEach(r->categories.merge(r.categoryCode(),1L,Long::sum));
        Files.writeString(data.resolve("manifests/article-acquisition.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("generatedAt",Instant.now().toString(),"resources",records.size(),"categoryDistribution",categories,"downloads",evidence,"method","Publisher titles and short metadata excerpts only; OpenStax sections explicitly identified; no full article text persisted in catalog; unknown difficulty/time/rating/date remain unknown")));
        System.out.println("ARTICLE_ACQUISITION "+records.size()+" "+categories);
    }
    private int count(String category){return (int)records.stream().filter(r->r.categoryCode().equals(category)).count();}
    static JsonNode state(String html,JsonMapper json) {
        var m=Pattern.compile("window\\.__PRELOADED_STATE__ = (.*?)</script>",Pattern.DOTALL).matcher(html);
        if(!m.find())throw new IllegalArgumentException("Publisher metadata state missing");
        return json.readTree(m.group(1).replaceFirst(";\\s*$",""));
    }
    private void leaves(JsonNode node,List<JsonNode> out){if(node.path("contents").isArray()){node.path("contents").forEach(n->leaves(n,out));}else out.add(node);}
    private void book(String slug,String category,int limit,List<String> topics) throws Exception {
        var root=state(download("https://openstax.org/books/"+slug+"/pages/preface"),json).path("content").path("book");
        String licenseUrl=root.path("license").path("url").asString();
        if(!licenseUrl.contains("/by-nc-sa/4.0")&&!licenseUrl.contains("/by/4.0"))throw new IllegalStateException("Unreviewed book license: "+slug);
        String license=licenseUrl.contains("/by-nc-sa/")?"CC-BY-NC-SA-4.0":"CC-BY-4.0";
        String source=license.equals("CC-BY-4.0")?"openstax-readings-ccby":"openstax-readings";
        var leaves=new ArrayList<JsonNode>();leaves(root.path("tree"),leaves);int accepted=0;
        var authorNames=new ArrayList<String>();root.path("authors").forEach(a->{String name=a.path("value").path("name").asString("");if(!name.isBlank())authorNames.add(name);});
        String authors=String.join(", ",authorNames);if(authors.length()>3000)authors="Contributing authors listed at the source";
        for(var node:leaves) {
            if(accepted>=limit)break;String page=node.path("slug").asString("");
            // Exclude book fronts, introductions, summaries, exercises and assessments. Individual explanatory sections only.
            if(!page.matches("[0-9]+-[0-9]+-.+")||page.matches(".*(summary|review|exercise|assessment|practice|key-terms|answer).*"))continue;
            String url="https://openstax.org/books/"+slug+"/pages/"+page;
            try {
                String html=download(url);var content=state(html,json).path("content");
                String title=EducationalResource.text(node.path("title").asString());
                String description=meta(html,"description");
                // This factual provenance label is not an invented article summary.
                description="Textbook reading section from "+EducationalResource.text(root.path("title").asString())+"."+(description==null?"":" Publisher excerpt: "+description);
                var tags=new ArrayList<>(topics);tags.add(EducationalResource.text(root.path("title").asString()));
                String actualCategory=category;
                if(slug.equals("college-success")&&page.startsWith("11-")){
                    actualCategory="HEALTH_FITNESS";tags.clear();tags.add("Healthy Habits");
                    if(title.toLowerCase(Locale.ROOT).contains("sleep"))tags.add("Sleep");
                    if(title.toLowerCase(Locale.ROOT).contains("physical"))tags.add("Fitness");
                    if(title.toLowerCase(Locale.ROOT).contains("nutrition"))tags.add("Nutrition Basics");
                    if(title.toLowerCase(Locale.ROOT).contains("mental"))tags.add("Wellness");
                }
                if(add(new EducationalResource(source,content.path("page").path("id").asString().split("@")[0],title,description,url,actualCategory,"ARTICLE",tags,"en",license,"OpenStax, Rice University. "+authors+". "+root.path("title").asString()+", "+title+". "+url+" — "+licenseUrl+"; metadata excerpt, no images. Retain attribution and applicable noncommercial/share-alike conditions.","title,url,language,book,license:publisher; description:publisher metadata excerpt with explicit section label; category:derived from book subject; type:ARTICLE denotes an individual textual reading section, not a whole book/course; authors in source attribution; difficulty,time,article publication date:unknown")))accepted++;
            }catch(java.io.IOException ex){System.out.println("HELD "+url+" "+ex.getMessage());}
        }
        System.out.println("BOOK "+slug+" accepted="+accepted);
    }
    static String meta(String html,String name) {
        var m=Pattern.compile("<meta[^>]*name=\""+Pattern.quote(name)+"\"[^>]*content=\"([^\"]*)\"",Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find()?EducationalResource.text(m.group(1)):null;
    }
    private void javaArticles() throws Exception {
        String toc=download("https://raw.githubusercontent.com/MicrosoftDocs/azure-dev-docs/main/articles/java/fundamentals/toc.yml");
        var links=Pattern.compile("href: ([a-z0-9-]+)\\.md").matcher(toc);
        while(links.find()) {
            String slug=links.group(1),url="https://learn.microsoft.com/en-us/azure/developer/java/fundamentals/"+slug;
            String html=download(url);String title=meta(html,"title"),description=meta(html,"description");
            if(title==null){var m=Pattern.compile("<title>(.*?)</title>",Pattern.DOTALL).matcher(html);if(m.find())title=EducationalResource.text(m.group(1)).replace(" | Microsoft Learn","");}
            if(title!=null)add(new EducationalResource("microsoft-learn",slug,title,description,url,"TECHNOLOGY","ARTICLE",List.of("Java","Cloud Computing"),"en","CC-BY-4.0","Microsoft and contributors. "+title+". "+url+". CC BY 4.0; https://github.com/MicrosoftDocs/azure-dev-docs/blob/main/LICENSE. Publisher metadata excerpt only.","title,description,url:publisher; language:English locale; type:individual explanatory/how-to article from Java fundamentals, not API reference; topics/category:derived from official Java fundamentals context; difficulty,time,date:unknown"));
        }
        download("https://raw.githubusercontent.com/MicrosoftDocs/azure-docs/main/LICENSE");
        String sqlUrl="https://learn.microsoft.com/en-us/azure/azure-sql/azure-sql-iaas-vs-paas-what-is-overview",sqlHtml=download(sqlUrl);
        var sqlTitle=Pattern.compile("<h1[^>]*>(.*?)</h1>",Pattern.DOTALL).matcher(sqlHtml);
        if(!sqlTitle.find())throw new IllegalStateException("SQL article title missing");
        add(new EducationalResource("microsoft-learn","azure-sql-overview",EducationalResource.text(sqlTitle.group(1)),meta(sqlHtml,"description"),sqlUrl,"TECHNOLOGY","ARTICLE",List.of("SQL","Databases","Cloud Computing"),"en","CC-BY-4.0","Microsoft and contributors. What is Azure SQL? "+sqlUrl+". https://github.com/MicrosoftDocs/azure-docs/blob/main/LICENSE — CC BY 4.0; metadata excerpt only.","title,description,url:publisher; language:English locale; type:individual conceptual comparison article, not API reference; topics/category:derived from official SQL subject; difficulty,time,date:unknown"));
    }
    private void genetics() throws Exception {
        String xml=download("https://medlineplus.gov/download/TopicIndex.xml");
        var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var nodes=f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getElementsByTagName("topic");int accepted=0;
        for(int i=0,limit=100-count("HEALTH_FITNESS");i<nodes.getLength()&&accepted<limit;i++) {
            var e=(Element)nodes.item(i);String url=direct(e,"url"),title=direct(e,"title");
            if(url==null||!url.matches("https://medlineplus.gov/genetics/understanding/[a-z0-9-]+/[a-z0-9-]+"))continue;
            String html=download(url),description=meta(html,"description");
            if(add(new EducationalResource("medline-genetics",url.substring(url.indexOf("/understanding/")+14),EducationalResource.text(title),description,url,"HEALTH_FITNESS","ARTICLE",List.of("Genetics","Health Education"),"en","US-GOV-PUBLIC-DOMAIN","Source: MedlinePlus Genetics, National Library of Medicine. "+title+". "+url+". General health education, not personalized medical advice; no third-party images.","title,url:official XML; description:publisher metadata; language:English index; type:individual Help Me Understand Genetics explanatory article; category/topics:derived from official series; difficulty,time,date:unknown")))accepted++;
        }
        System.out.println("GENETICS accepted="+accepted);
    }
    private static String direct(Element node,String tag){for(var c=node.getFirstChild();c!=null;c=c.getNextSibling())if(c instanceof Element e&&e.getTagName().equals(tag))return e.getTextContent();return null;}
    private boolean add(EducationalResource r) {
        var topics=new LinkedHashSet<>(r.topics());String text=(r.title()+" "+r.description()).toLowerCase(Locale.ROOT);
        if(r.categoryCode().equals("TECHNOLOGY")){
            if(text.matches("(?s).*\\bjava\\b.*"))topics.add("Java");
            if(text.matches("(?s).*\\bsql\\b.*"))topics.add("SQL");
            if(text.contains("data structure")||text.contains("algorithm"))topics.add("Algorithms and Data Structures");
            if(text.contains("artificial intelligence")){topics.add("Artificial Intelligence");topics.add("AI/ML");}
            if(text.contains("machine learning"))topics.add("Machine Learning");
            if(text.contains("database"))topics.add("Databases");
        }
        r=new EducationalResource(r.sourceId(),r.externalId(),r.title(),r.description(),r.canonicalUrl(),r.categoryCode(),r.resourceType(),List.copyOf(topics),r.language(),r.license(),r.attribution(),r.metadataOrigin()+"; topic aliases:derived only from title/description");
        String url=EducationalResource.canonical(r.canonicalUrl()),key=r.sourceId()+":"+EducationalResource.titleKey(r.title());
        if(r.title()==null||r.title().isBlank()||r.title().length()>150||r.externalId().isBlank()||r.externalId().length()>160)throw new IllegalArgumentException("Invalid publisher metadata: "+url);
        if(!urls.add(url)||!titles.add(key))return false;
        records.add(r);System.out.println("ACQUIRED "+records.size()+" "+r.title());return true;
    }
}
