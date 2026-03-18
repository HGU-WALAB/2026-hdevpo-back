package com.csee.swplus.mileage.auth.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Protects Swagger UI and API docs with HTTP Basic Auth.
 * Only requests with valid SWAGGER_AUTH_USER / SWAGGER_AUTH_PASSWORD can access.
 * Credentials should be set via env vars (never in committed config).
 */
@Slf4j
// Not @Component - using Spring Security httpBasic chain instead
public class SwaggerBasicAuthFilter extends org.springframework.web.filter.OncePerRequestFilter {

    // Match with /mileage (context path) or without (some deployments/proxies)
    private static final Pattern SWAGGER_PATH = Pattern.compile(
            "^(/mileage)?/(swagger-ui(\\.html)?|swagger-ui/.*|v3/api-docs.*)$");

    @Value("${swagger.auth.user:}")
    private String expectedUser;

    @Value("${swagger.auth.password:}")
    private String expectedPassword;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !SWAGGER_PATH.matcher(uri).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (expectedUser == null || expectedUser.isEmpty() || expectedPassword == null || expectedPassword.isEmpty()) {
            log.warn("Swagger Basic Auth disabled: SWAGGER_AUTH_USER and SWAGGER_AUTH_PASSWORD must be set");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Swagger access not configured");
            return;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Basic ")) {
            sendUnauthorized(response);
            return;
        }

        String decoded;
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(auth.substring(6));
            decoded = new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            sendUnauthorized(response);
            return;
        }

        int colon = decoded.indexOf(':');
        if (colon < 0) {
            sendUnauthorized(response);
            return;
        }

        String user = decoded.substring(0, colon);
        String pass = decoded.substring(colon + 1);

        if (expectedUser.equals(user) && expectedPassword.equals(pass)) {
            filterChain.doFilter(request, response);
        } else {
            sendUnauthorized(response);
        }
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Basic realm=\"Swagger\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}
