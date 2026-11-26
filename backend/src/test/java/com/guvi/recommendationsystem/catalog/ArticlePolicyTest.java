package com.guvi.recommendationsystem.catalog;

import com.guvi.recommendationsystem.service.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ArticlePolicyTest {
    private CatalogIngestionService service(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT * FROM catalog_sources ORDER BY source_id")).thenReturn(List.of(Map.of("source_id","openstax-readings","name","OpenStax","license","CC-BY-NC-SA-4.0")));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenAnswer(i->i.getArgument(0,String.class).contains("FROM categories")?1:0);
        return new CatalogIngestionService(jdbc,mock(ItemService.class));
    }
    private EducationalResource row(String id,String title,String url,String type,String license){return new EducationalResource("openstax-readings",id,title,"Textbook reading section from College Success.",url,"LEARNING_CAREER",type,List.of("Study Skills"),"en",license,"OpenStax, Rice University. College Success. CC BY-NC-SA 4.0.","title:publisher; type:individual reading section; difficulty,time,date:unknown");}
    @Test void articlePreviewAcceptsReviewedSourceAndRetainsDuplicateReview(){
        var p=service().preview(List.of(row("1","Understanding learning","https://openstax.org/books/college-success/pages/2-1-learning","ARTICLE","CC-BY-NC-SA-4.0"),row("2","Understanding learning","https://openstax.org/books/college-success/pages/2-2-learning","ARTICLE","CC-BY-NC-SA-4.0"),row("1","Understanding learning","https://openstax.org/books/college-success/pages/2-1-learning/","ARTICLE","CC-BY-NC-SA-4.0")));
        assertThat(p.canImport()).isTrue();assertThat(p.rows()).extracting(CatalogIngestionService.Row::decision).containsExactly("IMPORT","REVIEW","EXISTING");
    }
    @Test void rejectsWrongPublisherLicenseUnsupportedTypeAndPrivateDestinations(){
        for(var r:List.of(row("1","Learning","https://openstax.org/admin/private","ARTICLE","CC-BY-NC-SA-4.0"),row("1","Learning","https://openstax.org/books/college-success/pages/2-1-learning","BOOK","CC-BY-NC-SA-4.0"),row("1","Learning","https://openstax.org/books/college-success/pages/2-1-learning","ARTICLE","CC-BY-4.0"),row("1","Learning","https://127.0.0.1/books/x/pages/x","ARTICLE","CC-BY-NC-SA-4.0")))assertThat(service().preview(List.of(r)).canImport()).isFalse();
    }
    @Test void nullRecordProducesARejectedPreviewInsteadOfAnInternalFailure(){
        var preview=service().preview(Arrays.asList((EducationalResource)null));
        assertThat(preview.canImport()).isFalse();
        assertThat(preview.rows()).extracting(CatalogIngestionService.Row::decision).containsExactly("REJECT");
        assertThat(preview.rows().getFirst().reason()).isEqualTo("Source record is required.");
    }
    @Test void metadataParsingNeverNeedsExecutingPublisherScripts(){
        assertThat(ArticleAcquisition.meta("<meta name=\"description\" content=\"Study &amp; learn\">","description")).isEqualTo("Study & learn");
        assertThat(ArticleAcquisition.meta("<html></html>","description")).isNull();
        assertThatThrownBy(()->ArticleAcquisition.state("<script>bad()</script>",tools.jackson.databind.json.JsonMapper.builder().build())).isInstanceOf(IllegalArgumentException.class);
    }
}
