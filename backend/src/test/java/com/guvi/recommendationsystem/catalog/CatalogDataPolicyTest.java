package com.guvi.recommendationsystem.catalog;

import com.guvi.recommendationsystem.service.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.InetAddress;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CatalogDataPolicyTest {
    private EducationalResource row(String id,String title,String url,String license){return new EducationalResource("mit-ocw",id,title,null,url,"TECHNOLOGY","COURSE",List.of("Computer Science"),null,license,"MIT faculty. MIT OpenCourseWare. CC BY-NC-SA 4.0.","title,url,topics:provider; category:derived; difficulty,duration:unknown");}
    private JdbcTemplate database(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForList("SELECT * FROM catalog_sources ORDER BY source_id")).thenReturn(List.of(Map.of("source_id","mit-ocw","name","MIT OpenCourseWare","license","CC-BY-NC-SA-4.0")));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenAnswer(invocation->invocation.getArgument(0,String.class).contains("FROM categories")?1:0);
        return jdbc;
    }
    @Test void normalizesUnicodeMarkupWhitespaceWithoutInventingMetadata(){
        assertThat(EducationalResource.text(" <p>Cafe\u0301 &amp; Java</p><script>alert(1)</script>\n guide ")).isEqualTo("Café & Java guide");
        assertThat(EducationalResource.text(null)).isNull();
        assertThat(EducationalResource.titleKey(" Café: Programming! ")).isEqualTo(EducationalResource.titleKey("Cafe Programming"));
        assertThat(EducationalResource.canonical("https://ocw.mit.edu/courses/java/?utm_source=test")).isEqualTo("https://ocw.mit.edu/courses/java");
    }
    @Test void rejectsUnsafeHostsProtocolsPortsCredentialsAndPrivateAddresses()throws Exception {
        for(String value:List.of("http://ocw.mit.edu/courses/a","https://localhost/","https://127.0.0.1/","https://169.254.169.254/latest/meta-data/","https://ocw.mit.edu.evil.test/a","https://evil@ocw.mit.edu/a","https://ocw.mit.edu:8080/a","file:///etc/passwd","https://[::1]/"))assertThatThrownBy(()->SourceHttp.safeUri(value)).isInstanceOf(IllegalArgumentException.class);
        for(String value:List.of("10.0.0.1","172.16.1.1","192.168.0.1","100.64.0.1","::1","fc00::1","169.254.1.1","198.18.0.1","192.0.2.1","203.0.113.1","2001:db8::1"))assertThatThrownBy(()->SourceHttp.publicAddress(InetAddress.getByName(value))).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->SourceHttp.publicAddress(InetAddress.getByName("8.8.8.8"))).doesNotThrowAnyException();
    }
    @Test void dryRunAcceptsUnknownOptionalFieldsWithoutWriting(){
        var jdbc=database();var service=new CatalogIngestionService(jdbc,mock(ItemService.class));
        var result=service.preview(List.of(row("1","Java course","https://ocw.mit.edu/courses/java/","CC-BY-NC-SA-4.0")));
        assertThat(result.canImport()).isTrue();assertThat(result.imported()).isEqualTo(1);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void canonicalDuplicateAndAmbiguousEditionsHaveDifferentDecisions(){
        var jdbc=database();var service=new CatalogIngestionService(jdbc,mock(ItemService.class));
        var result=service.preview(List.of(row("1","Java course","https://ocw.mit.edu/courses/java/","CC-BY-NC-SA-4.0"),row("2","Java course","https://ocw.mit.edu/courses/java?ref=source","CC-BY-NC-SA-4.0"),row("3","Java Course!","https://ocw.mit.edu/courses/java-other","CC-BY-NC-SA-4.0")));
        assertThat(result.rows()).extracting(CatalogIngestionService.Row::decision).containsExactly("IMPORT","EXISTING","REVIEW");
    }
    @Test void invalidLicenseForeignSourceAndUntrustedHtmlRejectTheWholeApproval(){
        var service=new CatalogIngestionService(database(),mock(ItemService.class));
        assertThat(service.preview(List.of(row("1","Java course","https://ocw.mit.edu/courses/java","unknown"))).canImport()).isFalse();
        assertThat(service.preview(List.of(row("1","<script>bad</script>","https://ocw.mit.edu/courses/java","CC-BY-NC-SA-4.0"))).canImport()).isFalse();
        assertThat(service.preview(List.of(row("1","Valid title","https://medlineplus.gov/health.html","CC-BY-NC-SA-4.0"))).canImport()).isFalse();
    }
    @Test void categoryRulesUseSpecificOfficialSubjectsBeforeBroadCrossListings(){
        assertThat(RealDataAcquisition.category(List.of("Business","Computer Science"))).isEqualTo("TECHNOLOGY");
        assertThat(RealDataAcquisition.category(List.of("Fine Arts","Communication","Academic Writing"))).isEqualTo("LEARNING_CAREER");
        assertThat(RealDataAcquisition.category(List.of("Unmapped subject"))).isNull();
    }
    @Test void availabilityDistinguishesRestrictedFromMissingAndTransient(){
        assertThat(CatalogMaintenance.urlStatus(403,false)).isEqualTo("ACCESS_RESTRICTED");
        assertThat(CatalogMaintenance.urlStatus(404,false)).isEqualTo("NOT_FOUND");
        assertThat(CatalogMaintenance.urlStatus(503,false)).isEqualTo("TEMPORARILY_UNAVAILABLE");
        assertThat(CatalogMaintenance.urlStatus(200,true)).isEqualTo("REDIRECTED");
        var jdbc=mock(JdbcTemplate.class);new CatalogMaintenance(jdbc).scheduled();verifyNoInteractions(jdbc);
    }
}
