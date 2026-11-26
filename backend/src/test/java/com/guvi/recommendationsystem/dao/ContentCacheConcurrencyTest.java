package com.guvi.recommendationsystem.dao;
import com.guvi.recommendationsystem.catalog.CatalogImported;
import com.guvi.recommendationsystem.service.InteractionSignalWeights;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class ContentCacheConcurrencyTest {
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void invalidationCannotBeLostDuringConcurrentCorpusRebuild() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var started=new CountDownLatch(1);var release=new CountDownLatch(1);var invalidating=new CountDownLatch(1);var queries=new AtomicInteger();
        when(jdbc.query(anyString(),any(RowMapper.class))).thenAnswer(call->{
            if(queries.incrementAndGet()==1){started.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Test rebuild did not resume");}return List.of();});
        var dao=new JdbcRecommendationDao(jdbc,mock(InteractionSignalWeights.class));
        try(var threads=Executors.newVirtualThreadPerTaskExecutor()){
            var build=threads.submit(()->ReflectionTestUtils.invokeMethod(dao,"contentCorpus"));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            var invalidate=threads.submit(()->{invalidating.countDown();dao.catalogImported(new CatalogImported(3));});
            assertThat(invalidating.await(5,TimeUnit.SECONDS)).isTrue();release.countDown();
            build.get(5,TimeUnit.SECONDS);invalidate.get(5,TimeUnit.SECONDS);
            ReflectionTestUtils.invokeMethod(dao,"contentCorpus");assertThat(queries).hasValue(2);
            ReflectionTestUtils.invokeMethod(dao,"contentCorpus");assertThat(queries).hasValue(2);
        }
    }
}
