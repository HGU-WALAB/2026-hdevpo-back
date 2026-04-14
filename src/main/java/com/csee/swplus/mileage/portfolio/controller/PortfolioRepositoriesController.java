package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.GithubRepoCacheSyncResult;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.RepoPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.RepositoriesResponse;
import com.csee.swplus.mileage.portfolio.service.PortfolioService;
import com.csee.swplus.mileage.user.entity.Users;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * GitHub repository list & portfolio selection (same pattern as {@link PortfolioActivitiesController},
 * {@link PortfolioCvController}). Base path: /api/portfolio/repositories
 */
@RestController
@RequestMapping("/api/portfolio/repositories")
@RequiredArgsConstructor
@Tag(name = "Portfolio — Repositories", description = "GitHub 레포 캐시 목록, 캐시 갱신, 포트폴리오에 표시할 레포 설정")
public class PortfolioRepositoriesController {

    private final AuthService authService;
    private final PortfolioService portfolioService;

    /**
     * GET — DB 캐시 페이지 목록 + (선택된 레포에 한해) 커스텀 설정. 캐시는 POST …/github-cache/refresh 로 채움.
     */
    @GetMapping
    @Operation(
            summary = "GitHub 레포 목록 (캐시)",
            description =
                    "DB 캐시 기반 레포 목록 API입니다 (요청 시 GitHub API를 직접 호출하지 않습니다). "
                            + "데이터 조회 전 POST /github-cache/refresh 를 통해 캐시를 먼저 생성해야 합니다. "

                            + "visible_only=true인 경우 page/per_page는 무시되고 전체 레포가 반환됩니다. "

                            + "owner: owner_login 기준으로 조직/유저를 정확히 일치시켜 필터링합니다. "
                            + "search: 이름, owner, URL, 설명, 언어, repo_id, 커스텀 제목/설명에 대해 부분 일치 검색이며 공백은 AND 조건입니다. "
    )
    public ResponseEntity<RepositoriesResponse> getRepositories(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "per_page", required = false) Integer perPage,
            @RequestParam(value = "visible_only", required = false) Boolean visibleOnly,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "search", required = false) String search) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(
                portfolioService.getRepositories(
                        user, page, perPage, false, visibleOnly, null, null, owner, search));
    }

    /**
     * POST …/github-cache/refresh — GitHub list API로 캐시 행 갱신 (GET 핫패스에서 GitHub 미호출).
     */
    @PostMapping("/github-cache/refresh")
    @Operation(summary = "GitHub 레포 메타 캐시 갱신",
            description = "GitHub list API로 캐시 행 갱신. 상세·languages는 PUT/PATCH에서 보강. "
                    + "warnings: NO_GITHUB_TOKEN 등(토큰/공개 API 한계).")
    public ResponseEntity<GithubRepoCacheSyncResult> refreshGithubRepoCache() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.refreshGithubRepositoriesCache(user));
    }

    /**
     * PUT — 표시할 레포 목록 전체 동기화 (upsert + reorder + remove).
     */
    @PutMapping
    @Operation(summary = "레포 표시 설정 일괄 동기화")
    public ResponseEntity<RepositoriesResponse> putRepositories(@Valid @RequestBody List<RepoEntryRequest> request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putRepositories(user, request));
    }

    /**
     * PATCH …/{id} — portfolio 링크 PK(id)로 수정 + GitHub 보강.
     */
    @PatchMapping("/{id}")
    @Operation(summary = "단일 레포 설정 수정")
    public ResponseEntity<RepoEntryResponse> patchRepository(
            @PathVariable Long id,
            @RequestBody RepoPatchRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(
                portfolioService.patchRepository(user, id, request != null ? request : new RepoPatchRequest()));
    }

    private Users getCurrentUser() {
        String uniqueId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return authService.getLoginUser(uniqueId);
    }
}
