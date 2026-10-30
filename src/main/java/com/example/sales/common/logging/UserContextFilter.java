package com.example.sales.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/// Adds the authenticated caller to the logging MDC and to [RequestContext#CURRENT].
/// Registered in the security filter chain, after authentication has run.
public class UserContextFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        MDC.put(MDC_KEY, authentication.getName());
        try {
            var context = RequestContext.CURRENT.get().withUser(authentication.getName());
            CorrelationFilter.continueWith(context, chain, request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
