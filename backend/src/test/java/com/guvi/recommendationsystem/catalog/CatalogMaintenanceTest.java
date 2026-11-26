package com.guvi.recommendationsystem.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CatalogMaintenanceTest {
    @Test void concurrentTriggersDoNotOverlapAndReleaseTheirConnection()throws Exception {
        var jdbc=mock(JdbcTemplate.class);var source=mock(DataSource.class);var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);var result=mock(ResultSet.class);
        when(jdbc.getDataSource()).thenReturn(source);when(source.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenReturn(result);when(result.getInt(1)).thenReturn(1);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(jdbc.queryForList(anyString())).thenAnswer(call->{entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Fixture release timeout");return List.of();});
        var service=new CatalogMaintenance(jdbc);assertThat(service.trigger().get("state")).isEqualTo("STARTED");
        try{assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();assertThat(service.trigger().get("state")).isEqualTo("ALREADY_RUNNING");}finally{release.countDown();}
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(service.status().get("running").equals(true)&&System.nanoTime()<deadline)Thread.sleep(10);
        assertThat(service.status().get("running")).isEqualTo(false);assertThat(((Map<?,?>)service.status().get("last")).get("state")).isEqualTo("COMPLETED");verify(connection).close();
    }
    @Test void anotherInstanceHoldingTheDatabaseLeasePreventsChecks()throws Exception {
        var jdbc=mock(JdbcTemplate.class);var source=mock(DataSource.class);var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);var result=mock(ResultSet.class);
        when(jdbc.getDataSource()).thenReturn(source);when(source.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenReturn(result);when(result.getInt(1)).thenReturn(0);
        var service=new CatalogMaintenance(jdbc);service.trigger();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(service.status().get("running").equals(true)&&System.nanoTime()<deadline)Thread.sleep(10);
        assertThat(((Map<?,?>)service.status().get("last")).get("state")).isEqualTo("ANOTHER_INSTANCE_RUNNING");verify(jdbc,never()).queryForList(anyString());verify(connection).close();
    }
}
