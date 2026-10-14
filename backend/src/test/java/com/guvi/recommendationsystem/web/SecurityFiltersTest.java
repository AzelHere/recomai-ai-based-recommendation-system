package com.guvi.recommendationsystem.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFiltersTest {
    @Test
    void loginRateLimiterAllowsTenAttemptsAndRejectsTheNextWithRetryAfter() throws Exception {
        LoginRateLimitFilter filter = new LoginRateLimitFilter();
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (request, response) -> passed.incrementAndGet();
        MockHttpServletResponse response = new MockHttpServletResponse();
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("192.0.2.55");
            filter.doFilter(request, response, chain);
        }
        assertThat(passed).hasValue(10);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        assertThat(response.getContentAsString()).contains("Too many sign-in attempts");
    }

    @Test
    void securityHeadersDoNotEnableHstsForLocalHttp() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });
        assertThat(response.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }
}
