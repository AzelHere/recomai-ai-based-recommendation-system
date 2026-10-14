package com.guvi.recommendationsystem.dao;

import com.guvi.recommendationsystem.service.TopicPreferenceScorer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class JdbcUserTopicPreferenceDaoTest {
    @Test
    void searchQuerySeparatesWhereFromParameterizedTopicMatches() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        doReturn(List.of()).when(jdbcTemplate).queryForList(sql.capture(), eq(Long.class), any(Object[].class));

        new JdbcUserTopicPreferenceDao(jdbcTemplate, new TopicPreferenceScorer())
                .applySearch(17L, "Cloud computing courses", 0.12, 45);

        assertThat(sql.getValue()).contains("AND interest.is_active = TRUE\nWHERE LOCATE(?, LOWER(item.title))")
                .contains("ORDER BY tagged.interest_id")
                .doesNotContain("WHERELOCATE");
    }
}
