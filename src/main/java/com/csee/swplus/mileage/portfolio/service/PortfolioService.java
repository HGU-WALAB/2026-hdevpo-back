package com.csee.swplus.mileage.portfolio.service;

import com.csee.swplus.mileage.portfolio.dto.ActivityPatchItemRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityResponse;
import com.csee.swplus.mileage.portfolio.dto.ActivitiesResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageLinkRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageListResponse;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.RepoLanguageDto;
import com.csee.swplus.mileage.portfolio.dto.RepoPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.GithubRepoCacheSyncResult;
import com.csee.swplus.mileage.portfolio.dto.RepositoriesResponse;
import com.csee.swplus.mileage.portfolio.dto.SettingsResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackDomainPutDto;
import com.csee.swplus.mileage.portfolio.dto.TechStackDomainResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackEntryPutDto;
import com.csee.swplus.mileage.portfolio.dto.TechStackEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackPutRequest;
import com.csee.swplus.mileage.portfolio.dto.TechStackResponse;
import com.csee.swplus.mileage.portfolio.dto.ProfileLinkDto;
import com.csee.swplus.mileage.portfolio.dto.UserInfoResponse;
import com.csee.swplus.mileage.etcSubitem.repository.EtcSubitemRepository;
import com.csee.swplus.mileage.portfolio.entity.Portfolio;
import com.csee.swplus.mileage.portfolio.entity.PortfolioActivity;
import com.csee.swplus.mileage.portfolio.entity.PortfolioDomain;
import com.csee.swplus.mileage.portfolio.entity.PortfolioTechStackEntry;
import com.csee.swplus.mileage.portfolio.entity.PortfolioMileageEntry;
import com.csee.swplus.mileage.portfolio.entity.PortfolioGithubRepoCache;
import com.csee.swplus.mileage.portfolio.entity.PortfolioRepoEntry;
import com.csee.swplus.mileage.portfolio.repository.PortfolioActivityRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioGithubRepoCacheRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioDomainRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioMileageEntryRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioRepository;
import com.csee.swplus.mileage.portfolio.repository.PortfolioRepoEntryRepository;
import com.csee.swplus.mileage.github.util.TokenEncryptionUtil;
import com.csee.swplus.mileage.profile.entity.Profile;
import com.csee.swplus.mileage.profile.repository.ProfileRepository;
import com.csee.swplus.mileage.subitem.dto.SubitemNamesDto;
import com.csee.swplus.mileage.subitem.mapper.SubitemMapper;
import com.csee.swplus.mileage.user.entity.Users;
import com.csee.swplus.mileage.auth.exception.DoNotExistException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioDomainRepository portfolioDomainRepository;
    private final PortfolioRepoEntryRepository portfolioRepoEntryRepository;
    private final PortfolioGithubRepoCacheRepository portfolioGithubRepoCacheRepository;
    private final PortfolioActivityRepository portfolioActivityRepository;
    private final PortfolioMileageEntryRepository portfolioMileageEntryRepository;
    private final EtcSubitemRepository etcSubitemRepository;
    private final SubitemMapper subitemMapper;

    private final ProfileRepository profileRepository;
    private final RestTemplate restTemplate;

    @Value("${github.api-base-url}")
    private String githubApiBaseUrl;

    @Value("${github.token-encryption-key:}")
    private String tokenEncryptionKey;

    /**
     * Returns the portfolio for the user, creating one if it does not exist.
     */
    public Portfolio getOrCreatePortfolio(Users user) {
        return portfolioRepository
                .findByUser_Id(user.getId())
                .orElseGet(() -> portfolioRepository.save(
                        Portfolio.builder()
                                .user(user)
                                .build()));
    }

    /**
     * Fetches all languages for a repo via GET /repos/{owner}/{repo}/languages.
     * Returns languages sorted by byte count descending (primary first) with percentage. Limited to 15.
     */
    @SuppressWarnings("unchecked")
    private List<RepoLanguageDto> fetchRepoLanguages(String owner, String repoName, String githubToken) {
        if (owner == null || owner.isEmpty() || repoName == null || repoName.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String url = githubApiBaseUrl + "/repos/" + owner + "/" + repoName + "/languages";
            HttpEntity<Void> req;
            if (githubToken != null && !githubToken.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(githubToken);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                req = new HttpEntity<>(headers);
            } else {
                req = new HttpEntity<>(new HttpHeaders());
            }
            ResponseEntity<Map> res = restTemplate.exchange(url, HttpMethod.GET, req, Map.class);
            Map<String, Object> raw = res.getBody();
            if (raw == null || raw.isEmpty()) return Collections.emptyList();

            long totalBytes = raw.entrySet().stream()
                    .filter(e -> e.getValue() instanceof Number)
                    .mapToLong(e -> ((Number) e.getValue()).longValue())
                    .sum();
            if (totalBytes == 0) return Collections.emptyList();

            return raw.entrySet().stream()
                    .filter(e -> e.getValue() instanceof Number)
                    .sorted(Comparator.<Map.Entry<String, Object>>comparingLong(e -> ((Number) e.getValue()).longValue()).reversed())
                    .limit(15)
                    .map(e -> {
                        long bytes = ((Number) e.getValue()).longValue();
                        double pct = totalBytes > 0 ? 100.0 * bytes / totalBytes : 0;
                        return RepoLanguageDto.builder()
                                .name(e.getKey())
                                .percentage(Math.round(pct * 10) / 10.0)  // 1 decimal place
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    /**
     * GET /api/portfolio/user-info – 기본 정보 (학교 정보 + bio + profile_image_url).
     */
    public UserInfoResponse getUserInfo(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        return UserInfoResponse.builder()
                .name(user.getName())
                .department(user.getDepartment())
                .major1(user.getMajor1())
                .major2(user.getMajor2())
                .grade(user.getGrade())
                .semester(user.getSemester())
                .bio(portfolio.getBio())
                .profile_image_url(portfolio.getProfileImageUrl())
                .profile_links(portfolio.getProfileLinks() != null
                        ? new ArrayList<>(portfolio.getProfileLinks())
                        : new ArrayList<>())
                .build();
    }

    /**
     * PATCH /api/portfolio/user-info (JSON) 및 PUT /api/portfolio/user-info/image (파일)에서 사용.
     *
     * @param profileLinks {@code null} = do not change links; otherwise replace with normalized list (empty = clear).
     */
    public UserInfoResponse updateBio(Users user, String bio, String profileImageUrl, List<ProfileLinkDto> profileLinks) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        if (bio != null) {
            portfolio.setBio(bio);
        }
        if (profileImageUrl != null) {
            portfolio.setProfileImageUrl(profileImageUrl);
        }
        if (profileLinks != null) {
            portfolio.setProfileLinks(normalizeProfileLinks(profileLinks));
        }
        portfolioRepository.save(portfolio);
        return getUserInfo(user);
    }

    private static final int MAX_PROFILE_LINKS = 10;
    private static final int MAX_PROFILE_LINK_LABEL = 255;
    private static final int MAX_PROFILE_LINK_URL = 2048;

    private static List<ProfileLinkDto> normalizeProfileLinks(List<ProfileLinkDto> in) {
        List<ProfileLinkDto> out = new ArrayList<>();
        if (in == null) {
            return out;
        }
        for (ProfileLinkDto p : in) {
            if (p == null) {
                continue;
            }
            String url = p.getUrl() != null ? p.getUrl().trim() : "";
            if (url.isEmpty()) {
                continue;
            }
            if (url.length() > MAX_PROFILE_LINK_URL) {
                continue;
            }
            String label = p.getLabel() != null ? p.getLabel().trim() : "";
            if (label.isEmpty()) {
                label = url;
            } else if (label.length() > MAX_PROFILE_LINK_LABEL) {
                label = label.substring(0, MAX_PROFILE_LINK_LABEL);
            }
            out.add(ProfileLinkDto.builder().label(label).url(url).build());
            if (out.size() >= MAX_PROFILE_LINKS) {
                break;
            }
        }
        return out;
    }

    /**
     * GET /api/portfolio/tech-stack – domains + tech stacks (relational).
     */
    public TechStackResponse getTechStack(Users user) {
        getOrCreatePortfolio(user);
        String snum = user.getUniqueId();
        java.util.List<PortfolioDomain> domains = portfolioDomainRepository.findBySnumOrderByOrderIndexAscIdAsc(snum);
        java.util.List<TechStackDomainResponse> out = new java.util.ArrayList<>();
        for (PortfolioDomain d : domains) {
            java.util.List<TechStackEntryResponse> entries = new java.util.ArrayList<>();
            if (d.getTechStacks() != null) {
                for (PortfolioTechStackEntry t : d.getTechStacks()) {
                    entries.add(TechStackEntryResponse.builder()
                            .name(t.getName())
                            .level(t.getLevel())
                            .build());
                }
            }
            out.add(TechStackDomainResponse.builder()
                    .id(d.getId())
                    .name(d.getName())
                    .order_index(d.getOrderIndex())
                    .tech_stacks(entries)
                    .build());
        }
        return TechStackResponse.builder().domains(out).build();
    }

    /**
     * PUT /api/portfolio/tech-stack – full replace of domains and tech stacks.
     */
    public TechStackResponse putTechStack(Users user, TechStackPutRequest request) {
        getOrCreatePortfolio(user);
        String snum = user.getUniqueId();
        portfolioDomainRepository.deleteBySnum(snum);
        portfolioDomainRepository.flush();

        java.util.List<TechStackDomainPutDto> domains =
                request != null && request.getDomains() != null ? request.getDomains() : java.util.Collections.emptyList();
        int fallbackOrder = 0;
        for (TechStackDomainPutDto dto : domains) {
            if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
                continue;
            }
            int orderIndex = dto.getOrder_index() != null ? dto.getOrder_index() : fallbackOrder++;
            PortfolioDomain domain = PortfolioDomain.builder()
                    .snum(snum)
                    .name(dto.getName().trim())
                    .orderIndex(orderIndex)
                    .build();
            java.util.List<PortfolioTechStackEntry> techs = new java.util.ArrayList<>();
            if (dto.getTech_stacks() != null) {
                for (TechStackEntryPutDto t : dto.getTech_stacks()) {
                    if (t == null || t.getName() == null || t.getName().trim().isEmpty()) {
                        continue;
                    }
                    techs.add(PortfolioTechStackEntry.builder()
                            .snum(snum)
                            .domain(domain)
                            .name(t.getName().trim())
                            .level(clampLevel1To100(t.getLevel()))
                            .build());
                }
            }
            domain.setTechStacks(techs);
            portfolioDomainRepository.save(domain);
        }
        return getTechStack(user);
    }

    /** Single 1–100 score (no separate {@code score} column in DB). */
    private static int clampLevel1To100(Integer level) {
        if (level == null) {
            return 1;
        }
        return Math.max(1, Math.min(100, level));
    }

    /**
     * GET/PATCH response {@code description}: stored user text on the link when non-blank;
     * otherwise GitHub description (cache or live fetch). User clears override with PATCH {@code ""}.
     */
    private static String effectiveRepoDescription(String storedUserDescription, String githubDescription) {
        if (storedUserDescription != null && !storedUserDescription.trim().isEmpty()) {
            return storedUserDescription.trim();
        }
        if (githubDescription == null || githubDescription.trim().isEmpty()) {
            return null;
        }
        return githubDescription.trim();
    }

    /**
     * GET /api/portfolio/repositories – GitHub 레포 목록 + (선택된 레포에 한해) 커스텀 설정 정보.
     * Overload for internal callers (PUT, etc.): no filters.
     */
    public RepositoriesResponse getRepositories(Users user) {
        return getRepositories(user, 1, 100, null, null, null, null);
    }

    /**
     * GET /api/portfolio/repositories – GitHub 레포 목록 + (선택된 레포에 한해) 커스텀 설정 정보.
     * Pagination: ?page=&per_page=. Filters: ?selected_only=, ?visible_only=, ?sort=, ?visibility=.
     */
    public RepositoriesResponse getRepositories(Users user, Integer page, Integer perPage,
            Boolean selectedOnly, Boolean visibleOnly) {
        return getRepositories(user, page, perPage, selectedOnly, visibleOnly, null, null);
    }

    /**
     * Full getRepositories with sort and visibility. When GitHub is linked, the list is read only from
     * {@code _sw_mileage_portfolio_github_repo_cache} (paginated in memory). Run POST …/github-cache/refresh
     * to populate. There is no affiliation filter on GET (not stored per cache row). PATCH a repo to pull fresh
     * GitHub detail into the cache for that row.
     */
    public RepositoriesResponse getRepositories(Users user, Integer page, Integer perPage,
            Boolean selectedOnly, Boolean visibleOnly, String sort, String visibility) {
        int p = (page == null || page < 1) ? 1 : page;
        int limit = (perPage == null || perPage < 1) ? 30 : perPage;
        if (limit > 100) {
            limit = 100;
        }

        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<PortfolioRepoEntry> entries =
                portfolioRepoEntryRepository.findByPortfolio_IdOrderByDisplayOrderAsc(portfolio.getId());

        // Map of selected repos: GitHub repo_id -> PortfolioRepoEntry (custom settings)
        Map<Long, PortfolioRepoEntry> byRepoId = new HashMap<>();
        for (PortfolioRepoEntry e : entries) {
            byRepoId.put(e.getRepoId(), e);
        }

        String githubUsername = null;
        Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
        if (profile != null) {
            githubUsername = profile.getGithubUsername();
        }

        String sortParam = (sort != null && sort.matches("created|updated|pushed|full_name")) ? sort : "updated";
        String visibilityParam = (visibility != null && visibility.matches("all|public|private")) ? visibility : "all";

        java.util.List<RepoEntryResponse> list = new java.util.ArrayList<>();

        if (githubUsername != null && !githubUsername.isEmpty()) {
            List<PortfolioGithubRepoCache> cached =
                    portfolioGithubRepoCacheRepository.findByPortfolio_Id(portfolio.getId());
            List<PortfolioGithubRepoCache> filtered = new ArrayList<>(cached);
            if ("public".equals(visibilityParam)) {
                filtered.removeIf(c -> !"public".equals(c.getVisibility()));
            } else if ("private".equals(visibilityParam)) {
                filtered.removeIf(c -> !"private".equals(c.getVisibility()));
            }
            Comparator<PortfolioGithubRepoCache> cmp;
            if ("created".equals(sortParam)) {
                cmp = Comparator.comparing(
                        PortfolioGithubRepoCache::getGithubCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
            } else if ("full_name".equals(sortParam)) {
                cmp = Comparator.comparing(
                        c -> (c.getOwnerLogin() != null ? c.getOwnerLogin() : "") + "/"
                                + (c.getName() != null ? c.getName() : ""));
            } else {
                // updated, pushed — use stored github_updated_at (nulls last, newest first by ISO-8601 string)
                cmp = Comparator.comparing(
                        PortfolioGithubRepoCache::getGithubUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
            }
            filtered.sort(cmp);
            int from = (p - 1) * limit;
            int to = Math.min(from + limit, filtered.size());
            List<PortfolioGithubRepoCache> pageRows =
                    from >= filtered.size() ? Collections.emptyList() : filtered.subList(from, to);

            for (PortfolioGithubRepoCache c : pageRows) {
                long repoId = c.getRepoId();
                PortfolioRepoEntry selected = byRepoId.get(repoId);
                List<RepoLanguageDto> languages;
                if (c.getLanguages() != null && !c.getLanguages().isEmpty()) {
                    languages = new ArrayList<>(c.getLanguages());
                } else if (c.getPrimaryLanguage() != null && !c.getPrimaryLanguage().isEmpty()) {
                    languages = Collections.singletonList(
                            RepoLanguageDto.builder().name(c.getPrimaryLanguage()).percentage(null).build());
                } else {
                    languages = new ArrayList<>();
                }
                list.add(RepoEntryResponse.builder()
                        .id(selected != null ? selected.getId() : null)
                        .repo_id(repoId)
                        .custom_title(selected != null ? selected.getCustomTitle() : null)
                        .description(effectiveRepoDescription(
                                selected != null ? selected.getDescription() : null,
                                c.getGithubDescription()))
                        .github_description(c.getGithubDescription())
                        .is_visible(selected != null ? selected.getIsVisible() : false)
                        .display_order(selected != null ? selected.getDisplayOrder() : 0)
                        .name(c.getName())
                        .html_url(c.getHtmlUrl())
                        .language(c.getPrimaryLanguage())
                        .languages(languages)
                        .created_at(c.getGithubCreatedAt())
                        .updated_at(c.getGithubUpdatedAt())
                        .visibility(c.getVisibility())
                        .owner(c.getOwnerLogin())
                        .stargazers_count(c.getStargazersCount())
                        .forks_count(c.getForksCount())
                        .build());
            }
        } else {
            // No GitHub username connected: just return selected repos as before.
            for (PortfolioRepoEntry e : entries) {
                list.add(RepoEntryResponse.builder()
                        .id(e.getId())
                        .repo_id(e.getRepoId())
                        .custom_title(e.getCustomTitle())
                        .description(e.getDescription())
                        .github_description(null)
                        .is_visible(e.getIsVisible())
                        .display_order(e.getDisplayOrder())
                        .build());
            }
        }

        // Optional filters: selected_only (id != null) or visible_only (id != null && is_visible)
        if (Boolean.TRUE.equals(visibleOnly)) {
            list.removeIf(r -> r.getId() == null || !Boolean.TRUE.equals(r.getIs_visible()));
        } else if (Boolean.TRUE.equals(selectedOnly)) {
            list.removeIf(r -> r.getId() == null);
        }

        return RepositoriesResponse.builder().repositories(list).build();
    }

    /**
     * Fetches one page of repos from GitHub (same rules as {@link #getRepositories} list call).
     */
    private Map[] fetchGithubReposPage(
            String githubUsername,
            String githubToken,
            int page,
            int perPage,
            String sortParam,
            String visibilityParam,
            String affiliationParam) {
        if (githubToken != null && !githubToken.isEmpty()) {
            String url = UriComponentsBuilder
                    .fromHttpUrl(githubApiBaseUrl + "/user/repos")
                    .queryParam("sort", sortParam)
                    .queryParam("direction", "desc")
                    .queryParam("per_page", perPage)
                    .queryParam("page", page)
                    .queryParam("visibility", visibilityParam)
                    .queryParam("affiliation", affiliationParam)
                    .toUriString();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.setAccept(java.util.Arrays.asList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);
            ResponseEntity<Map[]> res = restTemplate.exchange(url, HttpMethod.GET, req, Map[].class);
            return res.getBody();
        }
        String url = UriComponentsBuilder
                .fromHttpUrl(githubApiBaseUrl + "/users/" + githubUsername + "/repos")
                .queryParam("type", "owner")
                .queryParam("sort", sortParam)
                .queryParam("direction", "desc")
                .queryParam("per_page", perPage)
                .queryParam("page", page)
                .toUriString();
        return restTemplate.getForObject(url, Map[].class);
    }

    /**
     * Cache refresh: GitHub list API only (no per-repo {@code /languages}). Upserts list fields:
     * name, url, primary language, dates, visibility, owner, stars, forks. Does not clear existing
     * {@code languages_json} on rows already enriched via PUT/PATCH.
     * <p>
     * Full language breakdown is written on {@code PUT/PATCH /portfolio/repositories} for selected repos.
     */
    public GithubRepoCacheSyncResult refreshGithubRepositoriesCache(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
        String githubUsername = profile != null ? profile.getGithubUsername() : null;
        List<String> warnings = new ArrayList<>();
        if (githubUsername == null || githubUsername.isEmpty()) {
            warnings.add("NO_GITHUB_USERNAME: GitHub username is not set on the profile; nothing to sync.");
            return GithubRepoCacheSyncResult.builder().reposSynced(0).warnings(warnings).build();
        }
        String githubToken = null;
        boolean hasEncryptedToken =
                profile.getGithubAccessToken() != null && !profile.getGithubAccessToken().isEmpty();
        if (tokenEncryptionKey == null || tokenEncryptionKey.isEmpty()) {
            if (hasEncryptedToken) {
                warnings.add(
                        "GITHUB_TOKEN_KEY_MISSING: github.token-encryption-key is not configured; "
                                + "stored token cannot be decrypted. Using public GET /users/{username}/repos?type=owner only.");
            } else {
                warnings.add(
                        "NO_GITHUB_TOKEN: No linked GitHub OAuth token; using public "
                                + "GET /users/{username}/repos?type=owner (owner-visible public repos only).");
            }
        } else if (hasEncryptedToken) {
            githubToken = TokenEncryptionUtil.decrypt(profile.getGithubAccessToken(), tokenEncryptionKey);
            if (githubToken == null || githubToken.isEmpty()) {
                warnings.add(
                        "GITHUB_TOKEN_UNAVAILABLE: Token could not be decrypted or is empty; "
                                + "using public GET /users/{username}/repos?type=owner.");
                githubToken = null;
            }
        } else {
            warnings.add(
                    "NO_GITHUB_TOKEN: No linked GitHub OAuth token; using public "
                            + "GET /users/{username}/repos?type=owner (owner-visible public repos only).");
        }
        if (githubToken != null && !githubToken.isEmpty()) {
            addGithubTokenProbeWarnings(githubToken, warnings);
        }
        String sortParam = "updated";
        String visibilityParam = "all";
        String affiliationParam = "owner,collaborator,organization_member";
        int perPage = 100;
        LocalDateTime now = LocalDateTime.now();
        int synced = 0;
        for (int p = 1; p <= 50; p++) {
            Map[] repos = fetchGithubReposPage(
                    githubUsername, githubToken, p, perPage, sortParam, visibilityParam, affiliationParam);
            if (repos == null || repos.length == 0) {
                break;
            }
            for (Map repo : repos) {
                if (repo == null) {
                    continue;
                }
                Object idObj = repo.get("id");
                if (!(idObj instanceof Number)) {
                    continue;
                }
                long repoId = ((Number) idObj).longValue();
                String name = (String) repo.get("name");
                String htmlUrl = (String) repo.get("html_url");
                String githubDescription = (String) repo.get("description");
                String language = (String) repo.get("language");
                Object cr = repo.get("created_at");
                Object ur = repo.get("updated_at");
                String createdAt = cr != null ? cr.toString() : null;
                String updatedAt = ur != null ? ur.toString() : null;
                Boolean isPrivate = (Boolean) repo.get("private");
                String vis = isPrivate != null && isPrivate ? "private" : "public";
                Integer stargazersCount = null;
                Object sc = repo.get("stargazers_count");
                if (sc instanceof Number) {
                    stargazersCount = ((Number) sc).intValue();
                }
                Integer forksCount = null;
                Object fc = repo.get("forks_count");
                if (fc instanceof Number) {
                    forksCount = ((Number) fc).intValue();
                }
                String ownerLogin = null;
                Object ownerObj = repo.get("owner");
                if (ownerObj instanceof Map) {
                    ownerLogin = (String) ((Map<?, ?>) ownerObj).get("login");
                }
                Optional<PortfolioGithubRepoCache> existingOpt =
                        portfolioGithubRepoCacheRepository.findByPortfolio_IdAndRepoId(portfolio.getId(), repoId);
                PortfolioGithubRepoCache row = existingOpt.orElseGet(() -> PortfolioGithubRepoCache.builder()
                        .portfolio(portfolio)
                        .repoId(repoId)
                        .build());
                row.setName(name);
                row.setHtmlUrl(htmlUrl);
                row.setGithubDescription(githubDescription);
                row.setPrimaryLanguage(language);
                row.setGithubCreatedAt(createdAt);
                row.setGithubUpdatedAt(updatedAt);
                row.setVisibility(vis);
                row.setOwnerLogin(ownerLogin);
                row.setStargazersCount(stargazersCount);
                row.setForksCount(forksCount);
                row.setGithubSyncedAt(now);
                if (!existingOpt.isPresent()) {
                    row.setLanguages(new ArrayList<>());
                }
                portfolioGithubRepoCacheRepository.save(row);
                synced++;
            }
        }
        return GithubRepoCacheSyncResult.builder().reposSynced(synced).warnings(warnings).build();
    }

    /**
     * Optional GET /user to surface auth/scope issues without failing the whole refresh.
     */
    private void addGithubTokenProbeWarnings(String githubToken, List<String> warnings) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(githubToken);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    githubApiBaseUrl + "/user",
                    HttpMethod.GET,
                    req,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            String scopes = response.getHeaders().getFirst("X-OAuth-Scopes");
            if (scopes != null && !scopes.trim().isEmpty() && !scopes.contains("repo")) {
                warnings.add(
                        "GITHUB_SCOPE: OAuth token does not include the `repo` scope; private repositories may be omitted. "
                                + "Fine-grained personal access tokens use repository permissions instead of classic scopes.");
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            warnings.add(
                    "GITHUB_UNAUTHORIZED: GitHub returned 401 for GET /user; token may be expired or revoked. "
                            + "Repo list may be incomplete—re-authorize GitHub.");
        } catch (Exception ignored) {
            // best-effort probe only
        }
    }

    private String resolveGithubToken(Users user) {
        if (tokenEncryptionKey == null || tokenEncryptionKey.isEmpty()) {
            return null;
        }
        Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
        if (profile == null || profile.getGithubAccessToken() == null
                || profile.getGithubAccessToken().isEmpty()) {
            return null;
        }
        return TokenEncryptionUtil.decrypt(profile.getGithubAccessToken(), tokenEncryptionKey);
    }

    /**
     * GET /repositories/{id} + /languages — full cache row for one repo (used when saving to portfolio).
     */
    private void enrichGithubRepoCacheForPortfolioRepo(Portfolio portfolio, Users user, long repoId) {
        String githubToken = resolveGithubToken(user);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> repo = restTemplate.getForObject(
                    githubApiBaseUrl + "/repositories/" + repoId, Map.class);
            if (repo == null) {
                return;
            }
            String name = (String) repo.get("name");
            String htmlUrl = (String) repo.get("html_url");
            String githubDescription = (String) repo.get("description");
            String language = (String) repo.get("language");
            Object ownerObj = repo.get("owner");
            String ownerLogin = null;
            if (ownerObj instanceof Map) {
                ownerLogin = (String) ((Map<?, ?>) ownerObj).get("login");
            }
            Object c = repo.get("created_at");
            Object u = repo.get("updated_at");
            String createdAt = c != null ? c.toString() : null;
            String updatedAt = u != null ? u.toString() : null;
            Boolean isPrivate = (Boolean) repo.get("private");
            String vis = isPrivate != null && isPrivate ? "private" : "public";
            Integer stargazersCount = null;
            Object sc = repo.get("stargazers_count");
            if (sc instanceof Number) {
                stargazersCount = ((Number) sc).intValue();
            }
            Integer forksCount = null;
            Object fc = repo.get("forks_count");
            if (fc instanceof Number) {
                forksCount = ((Number) fc).intValue();
            }
            List<RepoLanguageDto> languages = fetchRepoLanguages(ownerLogin, name, githubToken);
            if (languages.isEmpty() && language != null && !language.isEmpty()) {
                languages = Collections.singletonList(
                        RepoLanguageDto.builder().name(language).percentage(null).build());
            }
            upsertGithubRepoCacheFull(portfolio, repoId, name, htmlUrl, githubDescription, language, languages,
                    createdAt, updatedAt, vis, ownerLogin, stargazersCount, forksCount);
        } catch (Exception ex) {
            // best-effort cache
        }
    }

    private void upsertGithubRepoCacheFull(
            Portfolio portfolio,
            long repoId,
            String name,
            String htmlUrl,
            String githubDescription,
            String primaryLanguage,
            List<RepoLanguageDto> languages,
            String githubCreatedAt,
            String githubUpdatedAt,
            String visibility,
            String ownerLogin,
            Integer stargazersCount,
            Integer forksCount) {
        LocalDateTime now = LocalDateTime.now();
        PortfolioGithubRepoCache row = portfolioGithubRepoCacheRepository
                .findByPortfolio_IdAndRepoId(portfolio.getId(), repoId)
                .orElseGet(() -> PortfolioGithubRepoCache.builder()
                        .portfolio(portfolio)
                        .repoId(repoId)
                        .build());
        row.setName(name);
        row.setHtmlUrl(htmlUrl);
        row.setGithubDescription(githubDescription);
        row.setPrimaryLanguage(primaryLanguage);
        row.setLanguages(languages == null || languages.isEmpty() ? new ArrayList<>() : new ArrayList<>(languages));
        row.setGithubCreatedAt(githubCreatedAt);
        row.setGithubUpdatedAt(githubUpdatedAt);
        row.setVisibility(visibility);
        row.setOwnerLogin(ownerLogin);
        row.setStargazersCount(stargazersCount);
        row.setForksCount(forksCount);
        row.setGithubSyncedAt(now);
        portfolioGithubRepoCacheRepository.save(row);
    }

    /**
     * PATCH /api/portfolio/repositories/{id} – portfolio link row id로 단일 레포 수정.
     */
    public RepoEntryResponse patchRepository(Users user, Long id, RepoPatchRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioRepoEntry entry = portfolioRepoEntryRepository
                .findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 레포를 찾을 수 없습니다."));
        return applyRepositoryPatchAndEnrich(portfolio, entry, request != null ? request : new RepoPatchRequest(), user);
    }

    private RepoEntryResponse applyRepositoryPatchAndEnrich(
            Portfolio portfolio, PortfolioRepoEntry entry, RepoPatchRequest request, Users user) {
        if (request.getCustom_title() != null) {
            entry.setCustomTitle(request.getCustom_title());
        }
        if (request.getDescription() != null) {
            entry.setDescription(request.getDescription());
        }
        if (request.getIs_visible() != null) {
            entry.setIsVisible(request.getIs_visible());
        }
        if (request.getDisplay_order() != null) {
            entry.setDisplayOrder(request.getDisplay_order());
        }
        portfolioRepoEntryRepository.save(entry);

        String name = null;
        String htmlUrl = null;
        String githubDescription = null;
        String language = null;
        String ownerLogin = null;
        String createdAt = null;
        String updatedAt = null;
        Integer stargazersCount = null;
        Integer forksCount = null;
        String visibility = null;

        String githubToken = resolveGithubToken(user);
        Map<String, Object> repo = null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> fetched = restTemplate.getForObject(
                    githubApiBaseUrl + "/repositories/" + entry.getRepoId(),
                    Map.class);
            repo = fetched;
            if (repo != null) {
                name = (String) repo.get("name");
                htmlUrl = (String) repo.get("html_url");
                githubDescription = (String) repo.get("description");
                language = (String) repo.get("language");
                Object ownerObj = repo.get("owner");
                if (ownerObj instanceof Map) {
                    ownerLogin = (String) ((Map<?, ?>) ownerObj).get("login");
                }
                Object c = repo.get("created_at");
                Object u = repo.get("updated_at");
                createdAt = c != null ? c.toString() : null;
                updatedAt = u != null ? u.toString() : null;
                Object sc = repo.get("stargazers_count");
                if (sc instanceof Number) {
                    stargazersCount = ((Number) sc).intValue();
                }
                Object fc = repo.get("forks_count");
                if (fc instanceof Number) {
                    forksCount = ((Number) fc).intValue();
                }
                Boolean isPrivate = (Boolean) repo.get("private");
                visibility = isPrivate != null && isPrivate ? "private" : "public";
            }
        } catch (Exception ex) {
            // ignore GitHub errors here; return DB fields only
        }

        List<RepoLanguageDto> languages = fetchRepoLanguages(ownerLogin, name, githubToken);
        if (languages.isEmpty() && language != null && !language.isEmpty()) {
            languages = Collections.singletonList(
                    RepoLanguageDto.builder().name(language).percentage(null).build());
        }

        if (repo != null) {
            upsertGithubRepoCacheFull(portfolio, entry.getRepoId(), name, htmlUrl, githubDescription, language, languages,
                    createdAt, updatedAt, visibility, ownerLogin, stargazersCount, forksCount);
        }

        String githubForApi = githubDescription;
        if (githubForApi == null) {
            githubForApi = portfolioGithubRepoCacheRepository
                    .findByPortfolio_IdAndRepoId(portfolio.getId(), entry.getRepoId())
                    .map(PortfolioGithubRepoCache::getGithubDescription)
                    .orElse(null);
        }

        return RepoEntryResponse.builder()
                .id(entry.getId())
                .repo_id(entry.getRepoId())
                .custom_title(entry.getCustomTitle())
                .description(effectiveRepoDescription(entry.getDescription(), githubForApi))
                .github_description(githubForApi)
                .is_visible(entry.getIsVisible())
                .display_order(entry.getDisplayOrder())
                .name(name)
                .html_url(htmlUrl)
                .language(language)
                .languages(languages)
                .created_at(createdAt)
                .updated_at(updatedAt)
                .visibility(visibility)
                .owner(ownerLogin)
                .stargazers_count(stargazersCount)
                .forks_count(forksCount)
                .build();
    }

    /**
     * PUT /api/portfolio/repositories – 선택 목록 전체 동기화 (upsert + reorder + remove).
     * 요청 본문의 repo_id는 있으면 갱신, 없으면 생성하며, 요청에 없는 기존 링크는 삭제.
     */
    public RepositoriesResponse putRepositories(Users user, java.util.List<RepoEntryRequest> requests) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        Set<Long> requestedRepoIds = new HashSet<>();
        if (requests != null) {
            for (RepoEntryRequest r : requests) {
                if (r != null && r.getRepo_id() != null) {
                    requestedRepoIds.add(r.getRepo_id());
                }
            }
        }
        List<PortfolioRepoEntry> existing = portfolioRepoEntryRepository.findByPortfolio_IdOrderByDisplayOrderAsc(portfolio.getId());
        for (PortfolioRepoEntry e : existing) {
            if (!requestedRepoIds.contains(e.getRepoId())) {
                portfolioRepoEntryRepository.delete(e);
            }
        }
        portfolioRepoEntryRepository.flush();
        if (requests != null) {
            for (int i = 0; i < requests.size(); i++) {
                RepoEntryRequest r = requests.get(i);
                if (r == null || r.getRepo_id() == null) {
                    continue;
                }
                Optional<PortfolioRepoEntry> opt =
                        portfolioRepoEntryRepository.findByPortfolio_IdAndRepoId(portfolio.getId(), r.getRepo_id());
                PortfolioRepoEntry e = opt.orElseGet(() -> PortfolioRepoEntry.builder()
                        .portfolio(portfolio)
                        .repoId(r.getRepo_id())
                        .isVisible(true)
                        .build());
                if (r.getCustom_title() != null) {
                    e.setCustomTitle(r.getCustom_title());
                }
                if (r.getDescription() != null) {
                    e.setDescription(r.getDescription());
                }
                if (r.getIs_visible() != null) {
                    e.setIsVisible(r.getIs_visible());
                }
                e.setDisplayOrder(i);
                portfolioRepoEntryRepository.save(e);
            }
            for (RepoEntryRequest r : requests) {
                if (r != null && r.getRepo_id() != null
                        && portfolioRepoEntryRepository.findByPortfolio_IdAndRepoId(portfolio.getId(), r.getRepo_id()).isPresent()) {
                    enrichGithubRepoCacheForPortfolioRepo(portfolio, user, r.getRepo_id());
                }
            }
        }
        return getRepositories(user);
    }

    /**
     * GET /api/portfolio/activities – 활동 목록. Optional filter: ?category=1&category=2 (default: full list).
     */
    public ActivitiesResponse getActivities(Users user, java.util.List<String> categories) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<PortfolioActivity> list = (categories != null && !categories.isEmpty())
                ? portfolioActivityRepository.findByPortfolio_IdAndCategoryInOrderByCategoryAscDisplayOrderAsc(portfolio.getId(), categories)
                : portfolioActivityRepository.findByPortfolio_IdOrderByCategoryAscDisplayOrderAsc(portfolio.getId());
        java.util.List<ActivityResponse> responses = new java.util.ArrayList<>();
        for (PortfolioActivity a : list) {
            responses.add(toActivityResponse(a));
        }
        return ActivitiesResponse.builder().activities(responses).build();
    }

    /**
     * POST /api/portfolio/activities – 활동 추가 (반환된 id로 이후 PUT 가능).
     */
    public ActivityResponse createActivity(Users user, ActivityRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        int nextOrder = portfolioActivityRepository.findByPortfolio_IdOrderByCategoryAscDisplayOrderAsc(portfolio.getId()).size();
        PortfolioActivity activity = PortfolioActivity.builder()
                .portfolio(portfolio)
                .title(request.getTitle())
                .description(request.getDescription())
                .url(normalizeActivityUrl(request.getUrl()))
                .tags(normalizeActivityTags(request.getTags()))
                .startDate(request.getStart_date())
                .endDate(request.getEnd_date())
                .category(request.getCategory())
                .displayOrder(nextOrder)
                .build();
        activity = portfolioActivityRepository.save(activity);
        return toActivityResponse(activity);
    }

    /**
     * PUT /api/portfolio/activities/{id} – 활동 수정.
     */
    public ActivityResponse updateActivity(Users user, Long id, ActivityRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioActivity activity = portfolioActivityRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 활동을 찾을 수 없습니다."));
        activity.setTitle(request.getTitle());
        activity.setDescription(request.getDescription());
        activity.setUrl(normalizeActivityUrl(request.getUrl()));
        activity.setTags(normalizeActivityTags(request.getTags()));
        activity.setStartDate(request.getStart_date());
        activity.setEndDate(request.getEnd_date());
        activity.setCategory(request.getCategory());
        portfolioActivityRepository.save(activity);
        return toActivityResponse(activity);
    }

    /**
     * PATCH /api/portfolio/activities/{id} – 활동 일부 수정 (null이 아닌 필드만 반영).
     */
    public ActivityResponse patchActivity(Users user, Long id, ActivityPatchRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioActivity activity = portfolioActivityRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 활동을 찾을 수 없습니다."));
        if (request.getTitle() != null) {
            activity.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            activity.setDescription(request.getDescription());
        }
        if (request.getStart_date() != null) {
            activity.setStartDate(request.getStart_date());
        }
        if (request.getEnd_date() != null) {
            activity.setEndDate(request.getEnd_date());
        }
        if (request.getCategory() != null) {
            activity.setCategory(request.getCategory());
        }
        if (request.getUrl() != null) {
            activity.setUrl(normalizeActivityUrl(request.getUrl()));
        }
        if (request.getTags() != null) {
            activity.setTags(normalizeActivityTags(request.getTags()));
        }
        portfolioActivityRepository.save(activity);
        return toActivityResponse(activity);
    }

    /**
     * PATCH /api/portfolio/activities – 전체 목록 일부 수정 (각 항목은 id로 식별, null이 아닌 필드만 반영).
     */
    public ActivitiesResponse patchActivities(Users user, java.util.List<ActivityPatchItemRequest> request) {
        if (request == null || request.isEmpty()) {
            return getActivities(user, null);
        }
        Portfolio portfolio = getOrCreatePortfolio(user);
        for (ActivityPatchItemRequest item : request) {
            if (item == null || item.getId() == null) continue;
            PortfolioActivity activity = portfolioActivityRepository.findByIdAndPortfolio_Id(item.getId(), portfolio.getId()).orElse(null);
            if (activity == null) continue;
            if (item.getTitle() != null) activity.setTitle(item.getTitle());
            if (item.getDescription() != null) activity.setDescription(item.getDescription());
            if (item.getStart_date() != null) activity.setStartDate(item.getStart_date());
            if (item.getEnd_date() != null) activity.setEndDate(item.getEnd_date());
            if (item.getCategory() != null) activity.setCategory(item.getCategory());
            if (item.getUrl() != null) activity.setUrl(normalizeActivityUrl(item.getUrl()));
            if (item.getTags() != null) activity.setTags(normalizeActivityTags(item.getTags()));
            portfolioActivityRepository.save(activity);
        }
        return getActivities(user, null);
    }

    /**
     * DELETE /api/portfolio/activities/{id} – 활동 삭제.
     */
    public void deleteActivity(Users user, Long id) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioActivity activity = portfolioActivityRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 활동을 찾을 수 없습니다."));
        portfolioActivityRepository.delete(activity);
    }

    private static ActivityResponse toActivityResponse(PortfolioActivity a) {
        return ActivityResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .description(a.getDescription())
                .start_date(a.getStartDate())
                .end_date(a.getEndDate())
                .category(a.getCategory())
                .display_order(a.getDisplayOrder())
                .url(a.getUrl())
                .tags(a.getTags() != null ? new ArrayList<>(a.getTags()) : new ArrayList<>())
                .build();
    }

    private static String normalizeActivityUrl(String url) {
        if (url == null) {
            return null;
        }
        String t = url.trim();
        return t.isEmpty() ? null : t;
    }

    /** Trim, drop empties, preserve first-seen order (case-sensitive). */
    private static List<String> normalizeActivityTags(List<String> tags) {
        if (tags == null) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String s : tags) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty()) {
                seen.add(t);
            }
        }
        return new ArrayList<>(seen);
    }

    /**
     * PUT /api/portfolio/mileage – 전체 목록 교체 (repositories와 동일 패턴).
     * 기존 연결을 모두 삭제한 뒤 요청 목록으로 재등록. 순서는 request 배열 순서.
     */
    public MileageListResponse putMileageList(Users user, java.util.List<MileageEntryRequest> request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        
        // Validate request: check for duplicates and existence
        if (request != null && !request.isEmpty()) {
            java.util.Set<Long> seenIds = new java.util.HashSet<>();
            for (MileageEntryRequest req : request) {
                if (req == null || req.getMileage_id() == null) continue;
                // Check for duplicates in request
                if (seenIds.contains(req.getMileage_id())) {
                    throw new IllegalArgumentException("중복된 mileage_id가 있습니다: " + req.getMileage_id());
                }
                seenIds.add(req.getMileage_id());
                // Check that mileage_id exists in _sw_mileage_record
                if (!etcSubitemRepository.existsById(req.getMileage_id().intValue())) {
                    throw new DoNotExistException("마일리지 ID를 찾을 수 없습니다: " + req.getMileage_id());
                }
            }
        }
        
        java.util.List<PortfolioMileageEntry> current = portfolioMileageEntryRepository.findByPortfolio_IdOrderByDisplayOrderAsc(portfolio.getId());
        // 기존 연결 해제 시 원본 레코드의 is_linked_to_portfolio 플래그 해제
        for (PortfolioMileageEntry e : current) {
            etcSubitemRepository.findById(e.getMileageId().intValue())
                    .ifPresent(record -> {
                        record.setIsLinkedToPortfolio(false);
                        etcSubitemRepository.save(record);
                    });
        }
        portfolioMileageEntryRepository.deleteByPortfolio_Id(portfolio.getId());
        portfolioMileageEntryRepository.flush();

        if (request != null) {
            int order = 0;
            for (MileageEntryRequest req : request) {
                if (req == null || req.getMileage_id() == null) continue;
                PortfolioMileageEntry entry = PortfolioMileageEntry.builder()
                        .portfolio(portfolio)
                        .mileageId(req.getMileage_id())
                        .additionalInfo(req.getAdditional_info())
                        .displayOrder(order++)
                        .build();
                portfolioMileageEntryRepository.save(entry);
                etcSubitemRepository.findById(req.getMileage_id().intValue())
                        .ifPresent(record -> {
                            record.setIsLinkedToPortfolio(true);
                            etcSubitemRepository.save(record);
                        });
            }
        }
        return getMileageList(user);
    }

    /**
     * GET /api/portfolio/mileage – 연결된 마일리지 목록.
     */
    public MileageListResponse getMileageList(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<PortfolioMileageEntry> list = portfolioMileageEntryRepository.findByPortfolio_IdOrderByDisplayOrderAsc(portfolio.getId());
        java.util.List<MileageEntryResponse> responses = new java.util.ArrayList<>();
        for (PortfolioMileageEntry e : list) {
            responses.add(toMileageEntryResponse(e));
        }
        return MileageListResponse.builder().mileage(responses).build();
    }

    /**
     * POST /api/portfolio/mileage – 기존 마일리지 연결 (원본 마일리지는 삭제하지 않음).
     */
    public MileageEntryResponse linkMileage(Users user, MileageLinkRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        if (portfolioMileageEntryRepository.existsByPortfolio_IdAndMileageId(portfolio.getId(), request.getMileage_id())) {
            throw new IllegalArgumentException("이미 연결된 마일리지입니다.");
        }
        int nextOrder = portfolioMileageEntryRepository.findByPortfolio_IdOrderByDisplayOrderAsc(portfolio.getId()).size();
        PortfolioMileageEntry entry = PortfolioMileageEntry.builder()
                .portfolio(portfolio)
                .mileageId(request.getMileage_id())
                .additionalInfo(request.getAdditional_info())
                .displayOrder(nextOrder)
                .build();
        entry = portfolioMileageEntryRepository.save(entry);

        // 마일리지 원본 레코드에 포트폴리오 링크 플래그 설정 (옵션)
        etcSubitemRepository.findById(request.getMileage_id().intValue())
                .ifPresent(e -> {
                    e.setIsLinkedToPortfolio(true);
                    etcSubitemRepository.save(e);
                });

        return toMileageEntryResponse(entry);
    }

    /**
     * PUT /api/portfolio/mileage/{id} – 추가 설명(additional_info)만 수정 (id = portfolio_mileage link id).
     */
    public MileageEntryResponse updateMileageEntry(Users user, Long id, String additionalInfo) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioMileageEntry entry = portfolioMileageEntryRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 마일리지 연결을 찾을 수 없습니다."));
        entry.setAdditionalInfo(additionalInfo);
        portfolioMileageEntryRepository.save(entry);
        return toMileageEntryResponse(entry);
    }

    /**
     * DELETE /api/portfolio/mileage/{id} – 연결 해제 (원본 마일리지 기록은 삭제하지 않음).
     */
    public void unlinkMileage(Users user, Long id) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioMileageEntry entry = portfolioMileageEntryRepository.findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 마일리지 연결을 찾을 수 없습니다."));
        portfolioMileageEntryRepository.delete(entry);

        // 마일리지 원본 레코드의 포트폴리오 링크 플래그 해제 (옵션)
        etcSubitemRepository.findById(entry.getMileageId().intValue())
                .ifPresent(e -> {
                    e.setIsLinkedToPortfolio(false);
                    etcSubitemRepository.save(e);
                });
    }

    private MileageEntryResponse toMileageEntryResponse(PortfolioMileageEntry e) {
        // 기본 포트폴리오 링크 정보
        MileageEntryResponse.MileageEntryResponseBuilder builder = MileageEntryResponse.builder()
                .id(e.getId())
                .mileage_id(e.getMileageId())
                .additional_info(e.getAdditionalInfo())
                .display_order(e.getDisplayOrder());

        // 원본 마일리지(_sw_mileage_record)에서 추가 정보 조회 (있으면 세팅)
        etcSubitemRepository.findById(e.getMileageId().intValue())
                .ifPresent(record -> {
                    builder.subitemId(record.getSubitemId());
                    builder.categoryId(record.getCategoryId());
                    builder.semester(record.getSemester());
                    builder.description1(record.getDescription1());

                    // 이름 정보는 subitem/category 테이블 join으로 조회
                    SubitemNamesDto names = subitemMapper.findNamesBySubitemId(record.getSubitemId());
                    if (names != null) {
                        builder.subitemName(names.getSubitemName());
                        builder.categoryName(names.getCategoryName());
                    }
                });

        return builder.build();
    }

    private static final java.util.List<String> DEFAULT_SECTION_ORDER =
            java.util.Arrays.asList("tech", "repo", "activities", "certificates", "mileage");

    /**
     * GET /api/portfolio/settings – 섹션 순서 (유저 정보는 프론트에서 상단 고정).
     */
    public SettingsResponse getSettings(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<String> order = portfolio.getSectionOrder();
        if (order == null || order.isEmpty()) {
            order = DEFAULT_SECTION_ORDER;
        }
        return SettingsResponse.builder().section_order(order).build();
    }

    /**
     * PUT /api/portfolio/settings – 섹션 순서 변경.
     */
    public SettingsResponse putSettings(Users user, java.util.List<String> sectionOrder) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        portfolio.setSectionOrder(sectionOrder != null && !sectionOrder.isEmpty() ? sectionOrder : DEFAULT_SECTION_ORDER);
        portfolioRepository.save(portfolio);
        return getSettings(user);
    }
}
