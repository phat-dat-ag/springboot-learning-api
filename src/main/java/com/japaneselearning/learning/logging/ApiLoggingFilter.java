package com.japaneselearning.learning.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.jspecify.annotations.NullMarked;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiLoggingFilter extends OncePerRequestFilter {

    @Override
    @NullMarked
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long startTime = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            try {
                MDC.put("httpMethod", request.getMethod());
                MDC.put("uri", request.getRequestURI());
                MDC.put("status", String.valueOf(response.getStatus()));
                MDC.put("elapsed_ms", String.valueOf(elapsedMs));

                log.info("API request completed");
            } finally {
                MDC.remove("httpMethod");
                MDC.remove("uri");
                MDC.remove("status");
                MDC.remove("elapsed_ms");
            }
        }
    }
}