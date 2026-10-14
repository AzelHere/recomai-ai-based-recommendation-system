package com.guvi.recommendationsystem.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class PasswordResetRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_ATTEMPTS = 6;
    private static final long WINDOW_MILLIS = 15 * 60_000L;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/auth/password-reset/request".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        AtomicInteger result = new AtomicInteger();
        Window window = windows.compute(request.getRemoteAddr(), (address, previous) -> {
            Window current = previous == null || now - previous.startedAt() >= WINDOW_MILLIS
                    ? new Window(now, new AtomicInteger()) : previous;
            result.set(current.attempts().incrementAndGet());
            return current;
        });
        if (windows.size() > 2048) windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        if (result.get() > MAX_ATTEMPTS) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(Math.max(1, (WINDOW_MILLIS - now + window.startedAt()) / 1000)));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"Too many requests\",\"message\":"
                    + "\"Too many password reset requests. Wait and try again.\",\"timestamp\":\"" + Instant.now() + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(long startedAt, AtomicInteger attempts) { }
}
