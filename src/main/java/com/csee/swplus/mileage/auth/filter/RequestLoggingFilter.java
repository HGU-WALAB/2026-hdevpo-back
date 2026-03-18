package com.csee.swplus.mileage.auth.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs HTTP method, path, status, and duration for each request.
 * Registered as a servlet filter with highest precedence (runs first).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends org.springframework.web.filter.OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNs = System.nanoTime();
        StatusCapturingResponseWrapper wrapper = new StatusCapturingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
        } finally {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            String method = request.getMethod();
            String path = request.getRequestURI();
            int status = wrapper.getCapturedStatus();
            log.info("{} {} {} {}ms", method, path, status, durationMs);
        }
    }

    /**
     * Wraps the response to capture the HTTP status code.
     */
    private static class StatusCapturingResponseWrapper extends HttpServletResponseWrapper {
        private final AtomicInteger status = new AtomicInteger(200);

        StatusCapturingResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            status.set(sc);
            super.setStatus(sc);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setStatus(int sc, String sm) {
            status.set(sc);
            super.setStatus(sc, sm);
        }

        @Override
        public void sendError(int sc) throws IOException {
            status.set(sc);
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            status.set(sc);
            super.sendError(sc, msg);
        }

        int getCapturedStatus() {
            return status.get();
        }
    }
}
