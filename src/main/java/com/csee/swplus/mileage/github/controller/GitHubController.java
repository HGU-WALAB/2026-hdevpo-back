package com.csee.swplus.mileage.github.controller;

import com.csee.swplus.mileage.auth.util.JwtUtil;
import com.csee.swplus.mileage.github.dto.GitHubStatusResponse;
import com.csee.swplus.mileage.github.service.GitHubOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Key;

@RestController
@RequestMapping("/api/mileage/github")
@RequiredArgsConstructor
@Slf4j
public class GitHubController {

    private final GitHubOAuthService oauthService;

    @Value("${github.client-id}")
    private String clientId;
    
    @Value("${github.redirect-uri}")
    private String redirectUri;
    
    @Value("${app.frontend-url}")
    private String frontendUrl;
    
    @Value("${custom.jwt.secret}")
    private String jwtSecret;

    /**
     * Initiates GitHub OAuth flow
     * Redirects user to GitHub authorization page
     */
    @GetMapping("/connect")
    public void connect(HttpServletResponse response) throws IOException {
        log.info("🔗 GitHub OAuth connect requested");
        
        String url = "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=read:user,repo";
        
        log.info("   Redirecting to GitHub: {}", url);
        response.sendRedirect(url);
    }

    /**
     * GitHub OAuth callback endpoint
     * GitHub redirects here after user authorizes
     */
    @GetMapping("/callback")
    public void callback(@RequestParam(required = false) String code,
                         @RequestParam(required = false) String error,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("🔄 GitHub OAuth Callback");
        log.info("═══════════════════════════════════════════════════════════");
        
        // Manually check for JWT token in cookies (since callback is excluded from filter)
        String accessToken = extractAccessTokenFromCookies(request);
        if (accessToken == null) {
            log.error("❌ No access token found in cookies. User must be logged in.");
            response.sendRedirect("http://walab.handong.edu/milestone25/login");
            return;
        }
        
        // Validate token and set authentication context
        try {
            Key signingKey = JwtUtil.getSigningKey(jwtSecret);
            String userId = JwtUtil.getUserId(accessToken, signingKey);
            log.info("   Authenticated user ID: {}", userId);
            // Set authentication in context for service to use
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken authToken =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userId, null, null);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception e) {
            log.error("❌ Invalid or expired token: {}", e.getMessage());
            response.sendRedirect("http://walab.handong.edu/milestone25/login");
            return;
        }
        
        if (error != null) {
            log.error("❌ GitHub OAuth error: {}", error);
            // Whitelist error param to avoid open redirect or XSS (GitHub sends: access_denied, etc.)
            String safeError = (error.length() > 64 || !error.matches("[a-zA-Z0-9_]+")) ? "oauth_error" : error;
            response.sendRedirect("http://walab.handong.edu/milestone25/my?github_error=" + safeError);
            return;
        }

        if (code == null || code.isEmpty()) {
            log.error("❌ No authorization code received from GitHub");
            response.sendRedirect("http://walab.handong.edu/milestone25/my?github_error=no_code");
            return;
        }
        // Reject oversized or suspicious code (GitHub codes are typically short)
        String trimmedCode = code.trim();
        if (trimmedCode.length() > 500) {
            log.error("❌ GitHub authorization code too long (possible abuse)");
            response.sendRedirect("http://walab.handong.edu/milestone25/my?github_error=invalid_code");
            return;
        }

        try {
            log.info("   Processing callback with code: {}...", trimmedCode.substring(0, Math.min(10, trimmedCode.length())));
            oauthService.handleCallback(trimmedCode);
            
            log.info("✅ GitHub connection successful");
            log.info("═══════════════════════════════════════════════════════════");
            
            // Redirect to MyPage: http://walab.handong.edu/milestone25/my
            String myPageUrl = "http://walab.handong.edu/milestone25/my";
            log.info("   Redirecting to MyPage: {}", myPageUrl);
            response.sendRedirect(myPageUrl);
            
        } catch (Exception e) {
            log.error("❌ Error processing GitHub callback: {}", e.getMessage(), e);
            // Redirect to MyPage with error parameter
            response.sendRedirect("http://walab.handong.edu/milestone25/my?github_error=connection_failed");
        }
    }

    /**
     * Disconnect GitHub account for the current user.
     * Clears GitHub id/username/connectedAt in profile. Requires authentication.
     */
    @DeleteMapping("/connect")
    public ResponseEntity<GitHubStatusResponse> disconnect() {
        log.info("🔌 GitHub disconnect requested");
        oauthService.disconnect();
        GitHubStatusResponse response = oauthService.checkStatus();
        return ResponseEntity.ok(response);
    }

    /**
     * Check GitHub connection status for current user
     * Returns whether user has GitHub connected and their GitHub username
     */
    @GetMapping("/status")
    public ResponseEntity<GitHubStatusResponse> status() {
        log.debug("📊 GitHub status check requested");
        GitHubStatusResponse response = oauthService.checkStatus();
        log.debug("   Status - Connected: {}, Username: {}", 
                response.isConnected(), response.getGithubUsername());
        return ResponseEntity.ok(response);
    }
    
    private String extractAccessTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
