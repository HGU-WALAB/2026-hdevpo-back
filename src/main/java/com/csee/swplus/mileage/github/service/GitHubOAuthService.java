package com.csee.swplus.mileage.github.service;

import com.csee.swplus.mileage.github.dto.GitHubOrgDto;
import com.csee.swplus.mileage.github.dto.GitHubStatusResponse;
import com.csee.swplus.mileage.github.util.TokenEncryptionUtil;
import com.csee.swplus.mileage.portfolio.repository.PortfolioGithubRepoCacheRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioRepoEntryRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioRepository;
import com.csee.swplus.mileage.profile.entity.Profile;
import com.csee.swplus.mileage.profile.repository.ProfileRepository;
import com.csee.swplus.mileage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubOAuthService {

    @Value("${github.client-id}")
    private String clientId;
    
    @Value("${github.client-secret}")
    private String clientSecret;
    
    @Value("${github.token-url}")
    private String tokenUrl;
    
    @Value("${github.api-base-url}")
    private String apiBaseUrl;

    @Value("${github.token-encryption-key:}")
    private String tokenEncryptionKey;

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioGithubRepoCacheRepository portfolioGithubRepoCacheRepository;
    private final PortfolioRepoEntryRepository portfolioRepoEntryRepository;
    private final RestTemplate restTemplate;

    @Transactional
    @SuppressWarnings("rawtypes")
    public void handleCallback(String code) {
        log.info("🔄 Handling GitHub OAuth callback with code: {}", code.substring(0, Math.min(10, code.length())) + "...");
        
        // Get current logged-in user
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("   Current user ID: {}", currentUserId);

        try {
            // 1. Exchange code -> access token
            log.info("   Step 1: Exchanging code for access token...");
            ResponseEntity<Map> tokenRes = restTemplate.postForEntity(
                    tokenUrl + "?client_id=" + clientId + "&client_secret=" + clientSecret + "&code=" + code,
                    null,
                    Map.class
            );

            String accessToken = (String) tokenRes.getBody().get("access_token");
            if (accessToken == null) {
                log.error("❌ Failed to get access token from GitHub");
                throw new RuntimeException("Failed to get GitHub access token");
            }
            log.info("   ✅ Access token obtained");

            // 2. Fetch GitHub user info
            log.info("   Step 2: Fetching GitHub user info...");
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setAccept(java.util.Arrays.asList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            ResponseEntity<Map> userRes = restTemplate.exchange(
                    apiBaseUrl + "/user",
                    HttpMethod.GET,
                    req,
                    Map.class
            );

            Map githubUser = userRes.getBody();
            Long githubId = githubUser.get("id") != null ? Long.valueOf(githubUser.get("id").toString()) : null;
            String githubUsername = githubUser.get("login") != null ? githubUser.get("login").toString() : null;
            String githubName = githubUser.get("name") != null ? githubUser.get("name").toString() : null;
            if (githubId == null || githubUsername == null || githubUsername.isEmpty()) {
                log.error("❌ GitHub user response missing id/login");
                throw new RuntimeException("Failed to fetch GitHub user identity");
            }
            
            log.info("   ✅ GitHub user info fetched - ID: {}, Username: {}, Name: {}", 
                    githubId, githubUsername, githubName);

            // 3. Save or update Profile
            log.info("   Step 3: Saving GitHub connection to profile...");
            Profile profile = profileRepository.findBySnum(currentUserId)
                    .orElse(Profile.builder()
                            .snum(currentUserId)
                            .build());

            profile.setGithubId(githubId);
            profile.setGithubUsername(githubUsername);
            profile.setGithubConnectedAt(LocalDateTime.now());
            // Optionally update github_link
            if (profile.getGithubLink() == null || profile.getGithubLink().isEmpty()) {
                profile.setGithubLink("https://github.com/" + githubUsername);
            }
            // Store encrypted token for fetching private/org repos (only if encryption key is configured)
            if (tokenEncryptionKey != null && !tokenEncryptionKey.isEmpty()) {
                String encrypted = TokenEncryptionUtil.encrypt(accessToken, tokenEncryptionKey);
                if (encrypted != null) {
                    profile.setGithubAccessToken(encrypted);
                    log.info("   ✅ GitHub access token stored (encrypted)");
                } else {
                    log.warn("   ⚠ Token encryption failed; private/org repos will not be available");
                }
            } else {
                log.warn("   ⚠ GITHUB_TOKEN_ENCRYPTION_KEY not set; token not stored. Private/org repos unavailable.");
            }

            profileRepository.save(profile);
            log.info("   ✅ GitHub connection saved successfully");
            log.info("   Profile ID: {}, GitHub Username: {}", profile.getId(), profile.getGithubUsername());

        } catch (Exception e) {
            log.error("❌ Error handling GitHub callback: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect GitHub account: " + e.getMessage(), e);
        }
    }

    public GitHubStatusResponse checkStatus() {
        try {
            // Get current logged-in user
            String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
            log.debug("Checking GitHub status for user: {}", currentUserId);

            Profile profile = profileRepository.findBySnum(currentUserId).orElse(null);
            
            if (profile != null && profile.getGithubId() != null) {
                log.debug("GitHub connected - Username: {}", profile.getGithubUsername());
                return GitHubStatusResponse.builder()
                        .connected(true)
                        .githubUsername(profile.getGithubUsername())
                        .build();
            } else {
                log.debug("GitHub not connected");
                return GitHubStatusResponse.builder()
                        .connected(false)
                        .githubUsername(null)
                        .build();
            }
        } catch (Exception e) {
            log.error("Error checking GitHub status: {}", e.getMessage(), e);
            return GitHubStatusResponse.builder()
                    .connected(false)
                    .githubUsername(null)
                    .build();
        }
    }

    /**
     * Lists GitHub organizations for the current user via {@code GET /user/orgs}.
     * Requires stored OAuth token (encrypted in profile). If token is unavailable, returns an empty list.
     */
    @SuppressWarnings({"rawtypes"})
    public List<GitHubOrgDto> listOrganizations() {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();

        Profile profile = profileRepository.findBySnum(currentUserId).orElse(null);
        if (profile == null) {
            return Collections.emptyList();
        }

        if (tokenEncryptionKey == null || tokenEncryptionKey.isEmpty()) {
            return Collections.emptyList();
        }

        String encrypted = profile.getGithubAccessToken();
        if (encrypted == null || encrypted.isEmpty()) {
            return Collections.emptyList();
        }

        String token = TokenEncryptionUtil.decrypt(encrypted, tokenEncryptionKey);
        if (token == null || token.isEmpty()) {
            return Collections.emptyList();
        }

        List<GitHubOrgDto> orgs = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            // paginate (per_page max 100)
            for (int page = 1; page <= 10; page++) {
                String url = apiBaseUrl + "/user/orgs?per_page=100&page=" + page;
                ResponseEntity<List> res = restTemplate.exchange(url, HttpMethod.GET, req, List.class);
                Object body = res.getBody();
                if (!(body instanceof List)) {
                    break;
                }
                List<?> items = (List<?>) body;
                if (items.isEmpty()) {
                    break;
                }
                for (Object o : items) {
                    if (!(o instanceof Map)) {
                        continue;
                    }
                    Map m = (Map) o;
                    Object idObj = m.get("id");
                    Long id = (idObj instanceof Number) ? ((Number) idObj).longValue() : null;
                    String owner = (String) m.get("login");
                    String avatarUrl = (String) m.get("avatar_url");
                    orgs.add(GitHubOrgDto.builder()
                            .id(id)
                            .owner(owner)
                            .avatarUrl(avatarUrl)
                            .build());
                }
                if (items.size() < 100) {
                    break;
                }
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }

        return orgs;
    }

    /**
     * Disconnect GitHub for the current user: clears all OAuth fields (including any orphaned encrypted
     * token), removes OAuth-style {@code github_link}, and deletes this portfolio’s GitHub repo cache and
     * selected repo entries so no GitHub-derived data remains without an active connection.
     */
    @Transactional
    public void disconnect() {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("🔌 Disconnecting GitHub for user: {}", currentUserId);

        Profile profile = profileRepository.findBySnum(currentUserId).orElse(null);
        if (profile == null) {
            log.debug("   No profile found for user, nothing to disconnect");
            return;
        }

        boolean hadGithubState =
                profile.getGithubId() != null
                        || profile.getGithubConnectedAt() != null
                        || (profile.getGithubUsername() != null && !profile.getGithubUsername().isEmpty())
                        || (profile.getGithubAccessToken() != null && !profile.getGithubAccessToken().isEmpty());
        if (!hadGithubState) {
            log.debug("   No GitHub OAuth state on profile, nothing to do");
            return;
        }

        profile.setGithubId(null);
        profile.setGithubUsername(null);
        profile.setGithubConnectedAt(null);
        profile.setGithubAccessToken(null);
        if (profile.getGithubLink() != null && profile.getGithubLink().startsWith("https://github.com/")) {
            profile.setGithubLink(null);
        }
        profileRepository.save(profile);

        userRepository
                .findByUniqueId(currentUserId)
                .flatMap(u -> portfolioRepository.findByUser_Id(u.getId()))
                .ifPresent(
                        p -> {
                            long pid = p.getId();
                            portfolioRepoEntryRepository.deleteByPortfolio_Id(pid);
                            portfolioGithubRepoCacheRepository.deleteByPortfolio_Id(pid);
                            log.info(
                                    "   Removed GitHub portfolio cache and repo selections for portfolio id {}",
                                    pid);
                        });

        log.info("   ✅ GitHub disconnected and token cleared for user: {}", currentUserId);
    }
}
