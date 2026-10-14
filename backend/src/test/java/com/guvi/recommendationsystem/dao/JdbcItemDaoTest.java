package com.guvi.recommendationsystem.dao;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class JdbcItemDaoTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void catalogFallbackIsActiveClearlyGeneralAndRetainsUserExclusions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        doReturn(List.of()).when(jdbc).query(sql.capture(), any(RowMapper.class), eq(17L), eq(17L), eq(10));

        new JdbcItemDao(jdbc).findDiscoveryForUser(17L, 10);

        assertThat(sql.getValue()).contains("item.is_active = TRUE")
                .contains("category.is_active = TRUE")
                .contains("interaction_type IN ('DISLIKE', 'HIDE')")
                .contains("interaction_type IN ('VIEW', 'CLICK', 'READ_PROGRESS')")
                .contains("ORDER BY item.is_editor_pick DESC, item.quality_score DESC")
                .doesNotContain("trending", "popular", "personalized");
    }
}
