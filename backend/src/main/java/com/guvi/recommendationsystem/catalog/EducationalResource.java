package com.guvi.recommendationsystem.catalog;

import java.util.List;
import java.text.Normalizer;

/** Unknown source fields stay null. Classifications and plain-text conversions are explicitly derived. */
public record EducationalResource(String sourceId, String externalId, String title, String description,
        String canonicalUrl, String categoryCode, String resourceType, List<String> topics,
        String language, String license, String attribution, String metadataOrigin) {
    public EducationalResource {
        topics = topics == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(topics));
    }

    public static String text(String input) {
        if(input==null)return null;
        return Normalizer.normalize(input.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>"," ")
                .replaceAll("<[^>]*>"," ").replace("&amp;","&").replace("&quot;","\"")
                .replace("&#39;","'").replace("&lt;","<").replace("&gt;",">").replace("&nbsp;"," "),Normalizer.Form.NFC)
                .replaceAll("[\\p{Cc}\\p{Z}\\s]+"," ").strip();
    }
    public static String canonical(String value) {
        var uri=SourceHttp.safeUri(value);
        String path=uri.getPath().replaceAll("/{2,}","/");
        if(path.endsWith("/")&&path.length()>1)path=path.substring(0,path.length()-1);
        // Reviewed catalog units have no meaningful query parameters; do not normalize arbitrary URLs this way.
        return "https://"+uri.getHost()+path;
    }
    public static String titleKey(String title){return Normalizer.normalize(title,Normalizer.Form.NFKD).toLowerCase(java.util.Locale.ROOT).replaceAll("\\p{M}+","").replaceAll("[^\\p{L}\\p{N}]+"," ").strip();}
}
