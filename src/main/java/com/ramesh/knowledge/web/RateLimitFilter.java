package com.ramesh.knowledge.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window limit for answer generation, which is the only endpoint that
 * costs a full corpus scan plus a model call.
 *
 * <p>Windows are kept per client address in memory, which suits a single instance
 * and is documented as such: behind a proxy, {@code getRemoteAddr()} is the proxy
 * unless the container is configured to trust forwarding headers. The filter runs
 * before request handling and returns the same JSON shape as other errors.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    static final Duration WINDOW = Duration.ofMinutes(1);

    private final int requestsPerMinute;
    private final ObjectMapper objectMapper;
    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.requests-per-minute:60}") int requestsPerMinute,
                           ObjectMapper objectMapper) {
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isLimited(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (allow(request.getRemoteAddr())) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        body.put("message", "too many questions, try again in a minute");
        body.put("details", java.util.List.of());
        String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        objectMapper.writeValue(response.getWriter(), body);
    }

    /**
     * Matches the question paths with or without a trailing slash, so a client
     * cannot slip past the limit by writing {@code /api/ask/}.
     */
    static boolean isLimited(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        String normalized = path.substring(0, end);
        return "/api/ask".equals(normalized) || "/api/ask/stream".equals(normalized);
    }

    boolean allow(String client) {
        String key = client == null ? "unknown" : client;
        Instant cutoff = Instant.now().minus(WINDOW);
        Deque<Instant> calls = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (calls) {
            while (!calls.isEmpty() && calls.peekFirst().isBefore(cutoff)) {
                calls.pollFirst();
            }
            if (calls.size() >= requestsPerMinute) {
                return false;
            }
            calls.addLast(Instant.now());
            return true;
        }
    }
}
