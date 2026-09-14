package com.example.project2.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Interview topic: docs/interview/concurrency/03-async-pitfalls.md#context-propagation
 * (this filter is what puts the correlation id in MDC; ContextPropagatingTaskDecorator is what
 * carries it onto the @Async pool thread)
 *
 * <p>MDC is a ThreadLocal, so this id is only visible on the request thread unless something copies
 * it. Every async log line and every audit row would otherwise be unattributable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    /** Long enough for a UUID, short enough to fit the audit column. */
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat threads are pooled, so leaving the id behind would tag the next request with it.
            MDC.remove(MDC_KEY);
        }
    }

    /** Header values are client input, and an unfiltered one can forge a line in the log file. */
    private String sanitize(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String cleaned = headerValue.replaceAll("[^A-Za-z0-9_.:-]", "");
        if (cleaned.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
