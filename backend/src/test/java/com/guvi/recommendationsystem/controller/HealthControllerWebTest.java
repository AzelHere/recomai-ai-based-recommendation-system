package com.guvi.recommendationsystem.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerWebTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private JdbcTemplate jdbcTemplate;

    @Test void databaseFailureDoesNotExposeSqlOrCredentials() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1",Integer.class)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database credential and SQL"));
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The service is temporarily unavailable. Please try again shortly."))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-ID"));
    }

    @Test
    void healthEndpointConfirmsThatJdbcCanQueryTheDatabase() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));

        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }
}
