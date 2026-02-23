package com.csee.swplus.mileage.auth.filter;

import javax.servlet.http.Cookie;

import com.csee.swplus.mileage.auth.exception.WrongTokenException;
import lombok.extern.slf4j.Slf4j;
import com.csee.swplus.mileage.auth.exception.DoNotLoginException;
import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.auth.util.JwtUtil;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Key;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final Key SECRET_KEY;

    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            // Login/logout endpoints (with or without context path)
            "/api/mileage/auth/login$",
            "/milestone25/api/mileage/auth/login$",
            "/milestone25_1/api/mileage/auth/login$",
            "/mileage/api/mileage/auth/login$",
            "/api/mileage/auth/logout$",
            "/milestone25/api/mileage/auth/logout$",
            "/milestone25_1/api/mileage/auth/logout$",
            "/mileage/api/mileage/auth/logout$",
            // Public manager endpoints (contact, MyPage announcement, maintenance flag)
            "/api/mileage/contact$",
            "/api/mileage/announcement$",
            "/api/mileage/maintenance$",
            "/milestone25/api/mileage/contact$",
            "/milestone25/api/mileage/announcement$",
            "/milestone25/api/mileage/maintenance$",
            "/milestone25_1/api/mileage/contact$",
            "/milestone25_1/api/mileage/announcement$",
            "/milestone25_1/api/mileage/maintenance$",
            // GitHub OAuth callback (public - GitHub redirects here, but we check auth
            // manually)
            "/api/mileage/github/callback$",
            "/milestone25/api/mileage/github/callback$",
            "/milestone25_1/api/mileage/github/callback$",
            // Swagger paths (with or without context path)
            "^/swagger-ui",
            "^/v3/api-docs",
            "^/swagger-resources",
            "^/webjars",
            "^/milestone25/swagger-ui",
            "^/milestone25/v3/api-docs",
            "^/milestone25/swagger-resources",
            "^/milestone25/webjars",
            "^/milestone25_1/swagger-ui",
            "^/milestone25_1/v3/api-docs",
            "^/milestone25_1/swagger-resources",
            "^/milestone25_1/webjars");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        log.debug("🚀 JwtTokenFilter: 요청 URI: {}", requestURI);

        if (isExcludedPath(requestURI)) {
            log.debug("🔸 JwtTokenFilter: 제외된 경로입니다. 필터 체인 계속 진행.");
            filterChain.doFilter(request, response);
            return;
        }

        // Get token from Authorization header (for Swagger/Bearer token) or from
        // cookies
        String authHeader = request.getHeader("Authorization");
        String accessToken = null;
        String refreshToken = null;

        // Check Authorization header for Bearer token (used by Swagger)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7); // Remove "Bearer " prefix
        } else {
            // Fall back to cookies
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("accessToken".equals(cookie.getName())) {
                        accessToken = cookie.getValue();
                    }
                    if ("refreshToken".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                    }
                }
            }
        }

        // ✅ Check if both tokens are null before validation
        if (accessToken == null && refreshToken == null) {
            log.error("❌ JwtTokenFilter: accessToken과 refreshToken이 모두 존재하지 않습니다. 로그인 필요.");
            log.error("   Request URI: {}", requestURI);
            throw new DoNotLoginException();
        }

        // ✅ Try accessToken first (if available)
        if (accessToken != null) {
            try {
                String userId = JwtUtil.getUserId(accessToken, SECRET_KEY);
                Users loginUser = authService.getLoginUser(userId);

                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        loginUser.getUniqueId(), null, null);
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                // ✅ Success - continue filter chain
                filterChain.doFilter(request, response);
                return;
            } catch (WrongTokenException e) {
                log.info("❗ Access token validation failed: {}", e.getMessage());
                // Fall through to try refreshToken
            }
        }

        // ✅ Access token failed or is null - try refreshToken
        if (refreshToken != null) {
            try {
                String userId = JwtUtil.getUserId(refreshToken, SECRET_KEY);
                Users loginUser = authService.getLoginUser(userId);

                // 새로운 만료 시간으로 액세스 토큰 생성
                String newAccessToken = authService.createAccessToken(
                        loginUser.getUniqueId(),
                        loginUser.getName(),
                        loginUser.getEmail());

                // 새 액세스 토큰을 쿠키로 설정 (HttpOnly, SameSite=Lax, Secure when HTTPS)
                boolean secure = isSecureRequest(request);
                ResponseCookie cookie = ResponseCookie.from("accessToken", newAccessToken)
                        .path("/")
                        .maxAge(7200) // 토큰 만료 시간과 일치 (2시간)
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Lax")
                        .build();
                response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

                // 토큰 리프레시 확인용 로깅 추가
                log.info("🔄 사용자 {} 액세스 토큰 리프레시 성공", loginUser.getName());

                // 인증 설정
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        loginUser.getUniqueId(), null, null);
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                // ✅ Success - continue filter chain
                filterChain.doFilter(request, response);
                return;
            } catch (Exception refreshEx) {
                // 더 상세한 로깅을 포함한 개선된 예외 처리
                log.error("❌ 토큰 리프레시 실패: {}", refreshEx.getMessage());
                throw new DoNotLoginException();
            }
        } else {
            // ✅ Both tokens failed or are null
            log.error("❌ refreshToken이 존재하지 않습니다. 로그인이 필요합니다.");
            log.error("   Request URI: {}", requestURI);
            throw new DoNotLoginException();
        }
    }

    private boolean isExcludedPath(String requestURI) {
        log.debug("🔍 Checking if path is excluded: {}", requestURI);

        // Check regex patterns
        boolean matchesRegex = EXCLUDED_PATHS.stream().anyMatch(requestURI::matches);
        if (matchesRegex) {
            log.debug("✅ Path matches excluded regex pattern");
            return true;
        }

        // Fallback: check if URI contains Swagger-related paths (case-insensitive)
        String lowerURI = requestURI.toLowerCase();
        boolean isSwaggerPath = lowerURI.contains("/swagger-ui") ||
                lowerURI.contains("/v3/api-docs") ||
                lowerURI.contains("/swagger-resources") ||
                lowerURI.contains("/webjars");

        // Also check for login/logout and GitHub callback endpoints (more flexible
        // matching)
        boolean isAuthEndpoint = lowerURI.contains("/api/mileage/auth/login") ||
                lowerURI.contains("/api/mileage/auth/logout") ||
                lowerURI.contains("/api/mileage/github/callback");

        if (isSwaggerPath || isAuthEndpoint) {
            log.debug("✅ Path matches excluded path (fallback check)");
            return true;
        }

        log.debug("❌ Path is NOT excluded - authentication required");
        return false;
    }

    /**
     * Returns true if request is HTTPS (set Secure cookie to prevent transmission
     * over HTTP).
     */
    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure())
            return true;
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }
}