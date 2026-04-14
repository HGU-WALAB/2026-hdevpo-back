package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.ActivitiesResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageListResponse;
import com.csee.swplus.mileage.portfolio.dto.RepositoriesResponse;
import com.csee.swplus.mileage.portfolio.dto.SettingsResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackResponse;
import com.csee.swplus.mileage.portfolio.dto.UserInfoResponse;
import com.csee.swplus.mileage.portfolio.dto.CvPublicHtmlResult;
import com.csee.swplus.mileage.portfolio.service.PortfolioCvService;
import com.csee.swplus.mileage.portfolio.service.PortfolioHtmlExportService;
import com.csee.swplus.mileage.portfolio.service.PortfolioService;
import com.csee.swplus.mileage.portfolio.support.CvPublicHtmlFallbackPages;
import com.csee.swplus.mileage.user.entity.Users;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Public portfolio views by 학번 ({@code studentId} = {@link Users#getUniqueId()}), same idea as
 * {@code /api/mileage/share/{studentId}} — no login required.
 * Base path: {@code /api/portfolio/share}
 */
@RestController
@RequestMapping("/api/portfolio/share")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio — Public share", description = "학번으로 공개 포트폴리오 조회 (로그인 불필요). 레포는 포트폴리오에 표시(is_visible)된 항목만.")
public class PortfolioShareController {

    /** Declares the response body is HTML (actual markup is always in the body, not in header values). */
    private static final MediaType TEXT_HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final AuthService authService;
    private final PortfolioService portfolioService;
    private final PortfolioHtmlExportService htmlExportService;
    private final PortfolioCvService portfolioCvService;

    @GetMapping("/{studentId}/user-info")
    @Operation(summary = "[공개] 기본 프로필")
    public ResponseEntity<UserInfoResponse> getUserInfo(@PathVariable String studentId) {
        return ResponseEntity.ok(portfolioService.getUserInfo(resolveUser(studentId)));
    }

    @GetMapping("/{studentId}/tech-stack")
    @Operation(summary = "[공개] 기술 스택")
    public ResponseEntity<TechStackResponse> getTechStack(@PathVariable String studentId) {
        return ResponseEntity.ok(portfolioService.getTechStack(resolveUser(studentId)));
    }

    /**
     * GitHub 레포 목록 — 공개 시 {@code visible_only=true} 고정 (비공개로 숨긴 레포는 제외).
     */
    @GetMapping("/{studentId}/repositories")
    @Operation(
            summary = "[공개] 레포 목록",
            description = "visible_only is fixed (hidden repos excluded). page and per_page are ignored; the full visible list is returned. "
                    + "owner: owner_login exact match filter. "
                    + "search: 캐시 레포 메타·커스텀 제목 등 부분 일치(공백 AND). "
                    + "affiliation 쿼리는 지원하지 않음. "
                    + "GitHub API의 affiliation은 목록 API에서의 관계 필터이며 ‘커밋 이력이 있는 모든 레포’와 동일하지 않음.")
    public ResponseEntity<RepositoriesResponse> getRepositories(
            @PathVariable String studentId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "per_page", required = false) Integer perPage,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam(value = "search", required = false) String search) {
        Users user = resolveUser(studentId);
        return ResponseEntity.ok(
                portfolioService.getRepositories(user, page, perPage, false, true, null, null, owner, search));
    }

    @GetMapping("/{studentId}/mileage")
    @Operation(summary = "[공개] 연결 마일리지")
    public ResponseEntity<MileageListResponse> getMileage(@PathVariable String studentId) {
        return ResponseEntity.ok(portfolioService.getMileageList(resolveUser(studentId)));
    }

    @GetMapping("/{studentId}/activities")
    @Operation(summary = "[공개] 활동 목록")
    public ResponseEntity<ActivitiesResponse> getActivities(
            @PathVariable String studentId,
            @RequestParam(value = "category", required = false) List<String> categories) {
        return ResponseEntity.ok(portfolioService.getActivities(resolveUser(studentId), categories));
    }

    @GetMapping("/{studentId}/settings")
    @Operation(summary = "[공개] 섹션 순서")
    public ResponseEntity<SettingsResponse> getSettings(@PathVariable String studentId) {
        return ResponseEntity.ok(portfolioService.getSettings(resolveUser(studentId)));
    }

    @GetMapping(value = "/{studentId}/export/html", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "[공개] HTML 내보내기")
    public ResponseEntity<String> exportHtml(@PathVariable String studentId) {
        Users user = resolveUser(studentId);
        String html = htmlExportService.generateHtml(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"portfolio.html\"")
                .body(html);
    }

    /**
     * 이력서(CV) HTML by {@code publicToken}. See Swagger {@code @Operation} for status codes.
     */
    @GetMapping(value = "/cv/{publicToken}/html", produces = "text/html;charset=UTF-8")
    @Operation(
            summary = "[공개] 이력서 HTML (토큰)",
            description = "JWT 없이 호출 가능한 공개 이력서 뷰입니다. 대부분의 응답 본문은 HTML입니다(JSON 아님).\n\n"
                    + "**경로 변수 publicToken** — 숫자 토큰(통상 10자리, 8~12자리 허용). "
                    + "`POST /api/portfolio/cv/build-prompt` 또는 CV 목록/단건의 `public_token`.\n\n"
                    + "**공개 설정** — `PATCH /api/portfolio/cv/{id}` 의 `is_public: true` 일 때만 저장 HTML을 내려줄 수 있습니다.\n\n"
                    + "**상태 코드 (프론트 분기용)**\n"
                    + "- **200** · `is_public=true` 이고 `html_content`가 있음. 본문 = 저장된 HTML만. Content-Type: text/html; charset=UTF-8.\n"
                    + "- **204** · `is_public=true` 이지만 HTML이 아직 비어 있음. 본문 없음(RFC 204). 프론트에서 빈 화면/안내 UI 처리에 사용. "
                    + "(201 Created 는 '리소스 생성' 의미라 이 경우에 쓰지 않음.)\n"
                    + "- **403** · 이력서는 있으나 비공개. 안내 HTML 본문.\n"
                    + "- **404** · 토큰 없음 또는 형식 오류. 안내 HTML 본문.")

    public ResponseEntity<String> exportCvHtml(
            @Parameter(
                    name = "publicToken",
                    description = "이력서 공개 토큰(숫자 8~12자리). build-prompt / CV API의 public_token 값",
                    required = true,
                    in = ParameterIn.PATH)
            @PathVariable String publicToken) {
        CvPublicHtmlResult r = portfolioCvService.resolvePublicCvHtml(publicToken);
        switch (r.getKind()) {
        case OK:
            return ResponseEntity.ok()
                    .contentType(TEXT_HTML_UTF8)
                    .body(r.getHtmlBodyWhenOk());
        case PUBLIC_EMPTY:
            return new ResponseEntity<>(null, HttpStatus.NO_CONTENT);
        case PRIVATE:
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(TEXT_HTML_UTF8)
                    .body(CvPublicHtmlFallbackPages.privateCvPage());
        case NOT_FOUND:
        case INVALID_TOKEN:
        default:
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(TEXT_HTML_UTF8)
                    .body(CvPublicHtmlFallbackPages.notFoundPage());
        }
    }

    private Users resolveUser(String studentId) {
        return authService.getLoginUser(studentId);
    }
}
