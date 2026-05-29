package com.chatflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Attaches a correlationId to every REST request.
 *
 * If the caller supplies an X-Correlation-Id header (e.g. a gateway or
 * another service), that value is used — enabling end-to-end tracing
 * across service boundaries. Otherwise a fresh UUID is generated.
 *
 * The correlationId is placed in the MDC so Logback includes it in every
 * log line produced during the request without any manual passing.
 * It is also written to the X-Correlation-Id response header so the
 * client can correlate its own logs with server-side traces.
 *
 * MDC is always cleared in the finally block — critical for thread-pool
 * environments where threads are reused across requests.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY        = "correlationId";
    public static final String REQUEST_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(REQUEST_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(REQUEST_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}