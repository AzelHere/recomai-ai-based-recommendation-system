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
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_MILLIS = 60_000L;
    private final ConcurrentHashMap<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/api/auth/login".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String remoteAddress = request.getRemoteAddr();
        AtomicInteger result = new AtomicInteger();
        AttemptWindow window = windows.compute(remoteAddress, (key, previous) -> {
            AttemptWindow active = previous == null || now - previous.startedAt() >= WINDOW_MILLIS
                    ? new AttemptWindow(now, new AtomicInteger()) : previous;
            result.set(active.attempts().incrementAndGet());
            return active;
        });
        if (windows.size() > 2048) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        }
        if (result.get() > MAX_ATTEMPTS) {
            long remainingSeconds = Math.max(1, (WINDOW_MILLIS - (now - window.startedAt()) + 999) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(remainingSeconds));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"Too many requests\","
                    + "\"message\":\"Too many sign-in attempts. Wait briefly and try again.\","
                    + "\"timestamp\":\"" + Instant.now() + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private record AttemptWindow(long startedAt, AtomicInteger attempts) { }
}
