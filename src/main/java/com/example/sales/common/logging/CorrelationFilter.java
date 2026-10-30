package com.example.sales.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/// Assigns every request a correlation id, taken from the `X-Correlation-Id` header
/// or generated. The id is echoed in the response header, put in the logging MDC
/// and bound as [RequestContext#CURRENT] while the rest of the chain runs.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationFilter.class);
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var correlationId = Optional.ofNullable(request.getHeader(HEADER))
                .filter(id -> VALID_ID.matcher(id).matches())
                .orElseGet(() -> UUID.randomUUID().toString());
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            log.debug("Request started: {} {} query={}", request.getMethod(), request.getRequestURI(), request.getQueryString());
            continueWith(new RequestContext(correlationId, null), chain, request, response);
            log.debug("Request finished: {} {} status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /// Runs the rest of the filter chain with `context` bound as [RequestContext#CURRENT].
    static void continueWith(RequestContext context, FilterChain chain, HttpServletRequest request,
                             HttpServletResponse response) throws ServletException, IOException {
        try {
            ScopedValue.where(RequestContext.CURRENT, context).call(() -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
