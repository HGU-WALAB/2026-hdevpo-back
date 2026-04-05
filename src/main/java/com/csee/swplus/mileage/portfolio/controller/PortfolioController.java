package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageLinkRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageListResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageUpdateRequest;
import com.csee.swplus.mileage.portfolio.dto.SettingsPutRequest;
import com.csee.swplus.mileage.portfolio.dto.SettingsResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackPutRequest;
import com.csee.swplus.mileage.portfolio.dto.TechStackResponse;
import com.csee.swplus.mileage.portfolio.service.PortfolioHtmlExportService;
import com.csee.swplus.mileage.portfolio.service.PortfolioService;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.List;

/**
 * Portfolio "내 정보 모아보기" API (기술스택, 마일리지, 설정, 내보내기).
 * 레포는 {@link PortfolioRepositoriesController}, 프로필은 {@link PortfolioUserInfoController},
 * 활동은 {@link PortfolioActivitiesController}, CV는 {@link PortfolioCvController}.
 * Base path: /api/portfolio
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "내 정보 모아보기 — 기술스택, 마일리지, 설정, 내보내기. "
        + "레포·프로필·활동·CV는 각각 전용 태그를 참고하세요.")
public class PortfolioController {

    private final AuthService authService;
    private final PortfolioService portfolioService;
    private final PortfolioHtmlExportService htmlExportService;

    /**
     * GET /api/portfolio/tech-stack – 기술 스택 목록.
     */
    @GetMapping("/tech-stack")
    @Operation(summary = "기술 스택 조회", description = "domains + tech_stacks (level 1–100)")
    public ResponseEntity<TechStackResponse> getTechStack() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getTechStack(user));
    }

    /**
     * PUT /api/portfolio/tech-stack – domains + tech stacks 전체 교체 (스냅샷).
     * <ul>
     *   <li>도메인만 추가: {@code tech_stacks}를 빈 배열 {@code []}로 보내거나 생략(또는 null).</li>
     *   <li>도메인 전체 삭제: 요청 {@code domains} 배열에서 해당 도메인을 빼면 됨.</li>
     *   <li>도메인은 유지하고 기술만 비우기: 그 도메인에 {@code "tech_stacks": []}.</li>
     *   <li>개별 기술만 지우기: 해당 도메인의 {@code tech_stacks}에서 그 항목만 제거한 전체 목록을 보냄.</li>
     * </ul>
     * Body 예: { "domains": [ { "name": "Frontend", "order_index": 0, "tech_stacks": [ { "name": "React", "level": 73 } ] } ] }
     */
    @PutMapping("/tech-stack")
    @Operation(summary = "기술 스택 전체 교체", description = "PUT 스냅샷 — domains 배열 전체")
    public ResponseEntity<TechStackResponse> putTechStack(@Valid @RequestBody TechStackPutRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putTechStack(user, request != null ? request : new TechStackPutRequest()));
    }

    /**
     * GET /api/portfolio/mileage – 연결된 마일리지 목록.
     */
    @GetMapping("/mileage")
    @Operation(summary = "연결된 마일리지 목록")
    public ResponseEntity<MileageListResponse> getMileage() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getMileageList(user));
    }

    /**
     * PUT /api/portfolio/mileage – 전체 목록 교체 (repositories와 동일 패턴).
     * Body: [ { "mileage_id": 789, "additional_info": "설명" }, ... ]
     */
    @PutMapping("/mileage")
    @Operation(summary = "마일리지 연결 목록 전체 교체")
    public ResponseEntity<MileageListResponse> putMileage(@Valid @RequestBody List<MileageEntryRequest> request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putMileageList(user, request != null ? request : java.util.Collections.emptyList()));
    }

    /**
     * POST /api/portfolio/mileage – 기존 마일리지 연결.
     * Body: { "mileage_id": 789, "additional_info": "상세 설명" }
     */
    @PostMapping("/mileage")
    @Operation(summary = "마일리지 연결 추가")
    public ResponseEntity<MileageEntryResponse> postMileage(@Valid @RequestBody MileageLinkRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.linkMileage(user, request));
    }

    /**
     * PUT /api/portfolio/mileage/{id} – 추가 설명만 수정 (id = portfolio_mileage link id).
     * Body: { "additional_info": "내용 수정" }
     */
    @PutMapping("/mileage/{id}")
    @Operation(summary = "마일리지 연결 추가 설명 수정")
    public ResponseEntity<MileageEntryResponse> putMileage(@PathVariable Long id, @RequestBody MileageUpdateRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.updateMileageEntry(user, id, request != null ? request.getAdditional_info() : null));
    }

    /**
     * DELETE /api/portfolio/mileage/{id} – 연결 해제 (원본 마일리지는 삭제하지 않음).
     */
    @DeleteMapping("/mileage/{id}")
    @Operation(summary = "마일리지 연결 해제")
    public ResponseEntity<Void> deleteMileage(@PathVariable Long id) {
        Users user = getCurrentUser();
        portfolioService.unlinkMileage(user, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/portfolio/export/html – 포트폴리오 HTML 단일 파일 export (인쇄용, 공유용).
     */
    @GetMapping(value = "/export/html", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "포트폴리오 HTML 내보내기")
    public ResponseEntity<String> exportHtml() {
        Users user = getCurrentUser();
        String html = htmlExportService.generateHtml(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"portfolio.html\"")
                .body(html);
    }

    /**
     * GET /api/portfolio/export/prompt – 전체 LLM 프롬프트(ROLE, TASK, STEP 1-5)에 사용자 데이터를 STEP 2에 채워 반환.
     * 풀 테스트용 – 이 텍스트를 그대로 LLM에 붙여 넣어 포트폴리오 HTML 생성 가능.
     */
    @GetMapping(value = "/export/prompt", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "LLM용 전체 프롬프트(STEP 2 데이터 포함)")
    public ResponseEntity<String> exportPrompt() {
        Users user = getCurrentUser();
        String fullPrompt = htmlExportService.buildFullPrompt(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"portfolio-prompt.txt\"")
                .body(fullPrompt);
    }

    /**
     * GET /api/portfolio/settings – 섹션 순서 (유저 정보는 상단 고정).
     */
    @GetMapping("/settings")
    @Operation(summary = "섹션 순서 설정 조회")
    public ResponseEntity<SettingsResponse> getSettings() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getSettings(user));
    }

    /**
     * PUT /api/portfolio/settings – 섹션 레이아웃 순서 변경.
     * Body: { "section_order": ["tech", "repo", "activities", "mileage"] }
     */
    @PutMapping("/settings")
    @Operation(summary = "섹션 순서 설정 저장")
    public ResponseEntity<SettingsResponse> putSettings(@RequestBody SettingsPutRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putSettings(user, request != null ? request.getSection_order() : null));
    }

    private Users getCurrentUser() {
        String uniqueId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return authService.getLoginUser(uniqueId);
    }
}
