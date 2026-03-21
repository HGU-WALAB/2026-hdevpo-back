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
import com.csee.swplus.mileage.portfolio.dto.RepoPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.RepositoriesResponse;
import com.csee.swplus.mileage.portfolio.dto.SettingsResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackResponse;
import com.csee.swplus.mileage.portfolio.dto.UserInfoResponse;
import com.csee.swplus.mileage.etcSubitem.repository.EtcSubitemRepository;
import com.csee.swplus.mileage.portfolio.entity.Portfolio;
import com.csee.swplus.mileage.portfolio.entity.PortfolioActivity;
import com.csee.swplus.mileage.portfolio.entity.PortfolioMileageEntry;
import com.csee.swplus.mileage.portfolio.entity.PortfolioRepoEntry;
import com.csee.swplus.mileage.portfolio.repository.PortfolioActivityRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioRepoEntryRepository portfolioRepoEntryRepository;
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
     * Returns languages sorted by byte count descending (primary first). Limited to 15.
     */
    @SuppressWarnings("unchecked")
    private List<String> fetchRepoLanguages(String owner, String repoName, String githubToken) {
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
            return raw.entrySet().stream()
                    .filter(e -> e.getValue() instanceof Number)
                    .sorted(Comparator.<Map.Entry<String, Object>>comparingLong(e -> ((Number) e.getValue()).longValue()).reversed())
                    .limit(15)
                    .map(Map.Entry::getKey)
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
                .build();
    }

    /**
     * PATCH /api/portfolio/user-info – 소개글(bio) 및 프로필 이미지 수정.
     */
    public UserInfoResponse updateBio(Users user, String bio, String profileImageUrl) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        if (bio != null) {
            portfolio.setBio(bio);
        }
        if (profileImageUrl != null) {
            portfolio.setProfileImageUrl(profileImageUrl);
        }
        portfolioRepository.save(portfolio);
        return getUserInfo(user);
    }

    /**
     * GET /api/portfolio/tech-stack – 기술 스택 목록.
     */
    public TechStackResponse getTechStack(Users user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        return TechStackResponse.builder()
                .tech_stack(portfolio.getTechStack() != null ? portfolio.getTechStack() : java.util.Collections.emptyList())
                .build();
    }

    /**
     * PUT /api/portfolio/tech-stack – 기술 스택 전체 교체 (batch sync).
     */
    public TechStackResponse putTechStack(Users user, java.util.List<String> techStack) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        portfolio.setTechStack(techStack != null ? techStack : java.util.Collections.emptyList());
        portfolioRepository.save(portfolio);
        return getTechStack(user);
    }

    /**
     * GET /api/portfolio/repositories – GitHub 레포 목록 + (선택된 레포에 한해) 커스텀 설정 정보.
     * Overload for internal callers (PUT, etc.): no filters.
     */
    public RepositoriesResponse getRepositories(Users user) {
        return getRepositories(user, 1, 100, null, null, null, null, null);
    }

    /**
     * GET /api/portfolio/repositories – GitHub 레포 목록 + (선택된 레포에 한해) 커스텀 설정 정보.
     * Pagination: ?page=&per_page=. Filters: ?selected_only=, ?visible_only=, ?sort=, ?visibility=, ?affiliation=.
     */
    public RepositoriesResponse getRepositories(Users user, Integer page, Integer perPage,
            Boolean selectedOnly, Boolean visibleOnly) {
        return getRepositories(user, page, perPage, selectedOnly, visibleOnly, null, null, null);
    }

    /**
     * Full getRepositories with sort, visibility, affiliation. When token exists, uses GET /user/repos
     * (private + org repos). Otherwise falls back to public GET /users/{username}/repos.
     */
    public RepositoriesResponse getRepositories(Users user, Integer page, Integer perPage,
            Boolean selectedOnly, Boolean visibleOnly, String sort, String visibility, String affiliation) {
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

        // Find GitHub username and optionally decrypted token from Profile
        String githubUsername = null;
        String githubToken = null;
        Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
        if (profile != null) {
            githubUsername = profile.getGithubUsername();
            if (tokenEncryptionKey != null && !tokenEncryptionKey.isEmpty()
                    && profile.getGithubAccessToken() != null && !profile.getGithubAccessToken().isEmpty()) {
                githubToken = TokenEncryptionUtil.decrypt(profile.getGithubAccessToken(), tokenEncryptionKey);
            }
        }

        // Validate sort, visibility, affiliation for GitHub API
        String sortParam = (sort != null && sort.matches("created|updated|pushed|full_name")) ? sort : "updated";
        String visibilityParam = (visibility != null && visibility.matches("all|public|private")) ? visibility : "all";
        String affiliationParam = (affiliation != null && !affiliation.isEmpty()) ? affiliation : "owner,collaborator,organization_member";

        java.util.List<RepoEntryResponse> list = new java.util.ArrayList<>();

        if (githubUsername != null && !githubUsername.isEmpty()) {
            try {
                Map<String, Object>[] repos = null;
                if (githubToken != null) {
                    // Authenticated: GET /user/repos – includes private + org repos
                    String url = UriComponentsBuilder
                            .fromHttpUrl(githubApiBaseUrl + "/user/repos")
                            .queryParam("sort", sortParam)
                            .queryParam("direction", "desc")
                            .queryParam("per_page", limit)
                            .queryParam("page", p)
                            .queryParam("visibility", visibilityParam)
                            .queryParam("affiliation", affiliationParam)
                            .toUriString();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(githubToken);
                    headers.setAccept(java.util.Arrays.asList(MediaType.APPLICATION_JSON));
                    HttpEntity<Void> req = new HttpEntity<>(headers);
                    ResponseEntity<Map[]> res = restTemplate.exchange(url, HttpMethod.GET, req, Map[].class);
                    repos = res.getBody();
                } else {
                    // Unauthenticated: GET /users/{username}/repos – public owner repos only
                    String url = UriComponentsBuilder
                            .fromHttpUrl(githubApiBaseUrl + "/users/" + githubUsername + "/repos")
                            .queryParam("type", "owner")
                            .queryParam("sort", sortParam)
                            .queryParam("direction", "desc")
                            .queryParam("per_page", limit)
                            .queryParam("page", p)
                            .toUriString();
                    repos = restTemplate.getForObject(url, Map[].class);
                }

                if (repos != null) {
                    for (Map<String, Object> repo : repos) {
                        if (repo == null) continue;

                        Object idObj = repo.get("id");
                        if (!(idObj instanceof Number)) continue;
                        Long repoId = ((Number) idObj).longValue();

                        PortfolioRepoEntry selected = byRepoId.get(repoId);

                        String name = (String) repo.get("name");
                        String htmlUrl = (String) repo.get("html_url");
                        String language = (String) repo.get("language");
                        Object c = repo.get("created_at");
                        Object u = repo.get("updated_at");
                        String createdAt = c != null ? c.toString() : null;
                        String updatedAt = u != null ? u.toString() : null;
                        Boolean isPrivate = (Boolean) repo.get("private");
                        String vis = isPrivate != null && isPrivate ? "private" : "public";
                        String ownerLogin = null;
                        Object ownerObj = repo.get("owner");
                        if (ownerObj instanceof Map) {
                            ownerLogin = (String) ((Map<?, ?>) ownerObj).get("login");
                        }

                        List<String> languages = fetchRepoLanguages(ownerLogin, name, githubToken);
                        if (languages.isEmpty() && language != null && !language.isEmpty()) {
                            languages = Collections.singletonList(language);
                        }

                        list.add(RepoEntryResponse.builder()
                                .id(selected != null ? selected.getId() : null)
                                .repo_id(repoId)
                                .custom_title(selected != null ? selected.getCustomTitle() : null)
                                .description(selected != null ? selected.getDescription() : null)
                                .is_visible(selected != null ? selected.getIsVisible() : false)
                                .display_order(selected != null ? selected.getDisplayOrder() : 0)
                                .name(name)
                                .html_url(htmlUrl)
                                .language(language)
                                .languages(languages)
                                .created_at(createdAt)
                                .updated_at(updatedAt)
                                .visibility(vis)
                                .owner(ownerLogin)
                                .build());
                    }
                }
            } catch (Exception ex) {
                // If GitHub list call fails, fall back to DB-only selected repos (no GitHub fields).
                for (PortfolioRepoEntry e : entries) {
                    list.add(RepoEntryResponse.builder()
                            .id(e.getId())
                            .repo_id(e.getRepoId())
                            .custom_title(e.getCustomTitle())
                            .description(e.getDescription())
                            .is_visible(e.getIsVisible())
                            .display_order(e.getDisplayOrder())
                            .build());
                }
            }
        } else {
            // No GitHub username connected: just return selected repos as before.
            for (PortfolioRepoEntry e : entries) {
                list.add(RepoEntryResponse.builder()
                        .id(e.getId())
                        .repo_id(e.getRepoId())
                        .custom_title(e.getCustomTitle())
                        .description(e.getDescription())
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
     * PATCH /api/portfolio/repositories/{id} – 단일 레포 엔트리 일부 수정 (null이 아닌 필드만 반영).
     */
    public RepoEntryResponse patchRepository(Users user, Long id, RepoPatchRequest request) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        PortfolioRepoEntry entry = portfolioRepoEntryRepository
                .findByIdAndPortfolio_Id(id, portfolio.getId())
                .orElseThrow(() -> new DoNotExistException("해당 레포를 찾을 수 없습니다."));

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

        // Enrich with latest GitHub info for this repo (optional, best-effort)
        String name = null;
        String htmlUrl = null;
        String language = null;
        String ownerLogin = null;
        String createdAt = null;
        String updatedAt = null;

        String githubToken = null;
        if (tokenEncryptionKey != null && !tokenEncryptionKey.isEmpty()) {
            Profile profile = profileRepository.findBySnum(user.getUniqueId()).orElse(null);
            if (profile != null && profile.getGithubAccessToken() != null && !profile.getGithubAccessToken().isEmpty()) {
                githubToken = TokenEncryptionUtil.decrypt(profile.getGithubAccessToken(), tokenEncryptionKey);
            }
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> repo = restTemplate.getForObject(
                    githubApiBaseUrl + "/repositories/" + entry.getRepoId(),
                    Map.class);
            if (repo != null) {
                name = (String) repo.get("name");
                htmlUrl = (String) repo.get("html_url");
                language = (String) repo.get("language");
                Object ownerObj = repo.get("owner");
                if (ownerObj instanceof Map) {
                    ownerLogin = (String) ((Map<?, ?>) ownerObj).get("login");
                }
                Object c = repo.get("created_at");
                Object u = repo.get("updated_at");
                createdAt = c != null ? c.toString() : null;
                updatedAt = u != null ? u.toString() : null;
            }
        } catch (Exception ex) {
            // ignore GitHub errors here; return DB fields only
        }

        List<String> languages = fetchRepoLanguages(ownerLogin, name, githubToken);
        if (languages.isEmpty() && language != null && !language.isEmpty()) {
            languages = Collections.singletonList(language);
        }

        return RepoEntryResponse.builder()
                .id(entry.getId())
                .repo_id(entry.getRepoId())
                .custom_title(entry.getCustomTitle())
                .description(entry.getDescription())
                .is_visible(entry.getIsVisible())
                .display_order(entry.getDisplayOrder())
                .name(name)
                .html_url(htmlUrl)
                .language(language)
                .languages(languages)
                .created_at(createdAt)
                .updated_at(updatedAt)
                .build();
    }

    /**
     * PUT /api/portfolio/repositories – 전체 목록 교체 (batch sync).
     */
    public RepositoriesResponse putRepositories(Users user, java.util.List<RepoEntryRequest> requests) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        portfolioRepoEntryRepository.deleteByPortfolio_Id(portfolio.getId());
        portfolioRepoEntryRepository.flush();  // Force DELETE before INSERT to avoid unique constraint violation
        if (requests != null) {
            for (int i = 0; i < requests.size(); i++) {
                RepoEntryRequest r = requests.get(i);
                Boolean visible = r.getIs_visible() != null ? r.getIs_visible() : true;
                portfolioRepoEntryRepository.save(PortfolioRepoEntry.builder()
                        .portfolio(portfolio)
                        .repoId(r.getRepo_id())
                        .customTitle(r.getCustom_title())
                        .description(r.getDescription())
                        .isVisible(visible)
                        .displayOrder(i)
                        .build());
            }
        }
        return getRepositories(user);
    }

    /**
     * GET /api/portfolio/activities – 활동 목록. Optional filter: ?category=1&category=2 (default: full list).
     */
    public ActivitiesResponse getActivities(Users user, java.util.List<Integer> categories) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        java.util.List<PortfolioActivity> list = (categories != null && !categories.isEmpty())
                ? portfolioActivityRepository.findByPortfolio_IdAndCategoryInOrderByDisplayOrderAscStartDateDesc(portfolio.getId(), categories)
                : portfolioActivityRepository.findByPortfolio_IdOrderByDisplayOrderAscStartDateDesc(portfolio.getId());
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
        int nextOrder = portfolioActivityRepository.findByPortfolio_IdOrderByDisplayOrderAscStartDateDesc(portfolio.getId()).size();
        PortfolioActivity activity = PortfolioActivity.builder()
                .portfolio(portfolio)
                .title(request.getTitle())
                .description(request.getDescription())
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
                .build();
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
