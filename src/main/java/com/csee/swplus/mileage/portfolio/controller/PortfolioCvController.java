package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.*;
import com.csee.swplus.mileage.portfolio.service.PortfolioCvService;
import com.csee.swplus.mileage.user.entity.Users;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * CV/Resume Maker API. Grouped separately in Swagger under "CV (이력서)".
 * Base path: /api/portfolio/cv
 */
@RestController
@RequestMapping("/api/portfolio/cv")
@RequiredArgsConstructor
@Tag(name = "CV (이력서)", description = "이력서 생성 및 관리 (직무 정보 + 포트폴리오 선택 → 프롬프트 → HTML 제출)")
public class PortfolioCvController {

    private final AuthService authService;
    private final PortfolioCvService portfolioCvService;

    /**
     * POST /api/portfolio/cv/build-prompt – Build prompt and create CV (html blank).
     * Returns prompt, cv_id, public_token. User copies prompt to LLM, pastes HTML, then PATCH /cv/{id} with html_content / is_public.
     */
    @Operation(
            summary = "프롬프트 생성 및 CV 레코드 생성",
            description = "**mode** — `cv` (기본, 생략·null) 또는 `archive` (성찰·아카이브용 프롬프트). 그 외 문자열은 **400**. "
                    + "`archive`일 때 요청 필드 `job_posting`은 프롬프트 본문에서만 관심 영역으로 표시(필드명은 그대로). "
                    + "**design_preferences** (`{layout, color_theme, density, additional_notes}`) 가 있으면 STEP 2에 "
                    + "`[design_preferences]` 블록으로 한국어로 렌더링되며, CV에 저장되어 GET 응답에 포함됩니다 (빈 필드는 자동 생략). "
                    + "서버는 LLM을 호출하지 않습니다 — 사용자가 개인 AI에 붙여넣습니다.")
    @PostMapping("/build-prompt")
    public ResponseEntity<CvBuildPromptResponse> buildPrompt(@javax.validation.Valid @RequestBody CvBuildPromptRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.buildPrompt(user, request != null ? request : new CvBuildPromptRequest()));
    }

    /**
     * GET /api/portfolio/cv – List all CVs for the user.
     */
    @Operation(summary = "CV 목록",
            description = "로그인 사용자의 삭제되지 않은 CV 목록. **total** — 전체 개수 (repositories API와 동일 패턴). "
                    + "**sort** — `newest` (기본, 최신순) 또는 `favorites` (즐겨찾기 먼저, 그다음 최신순). "
                    + "각 항목에 **is_favorite** 포함.")
    @GetMapping
    public ResponseEntity<CvListResponse> list(@RequestParam(required = false) String sort) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.list(user, sort));
    }

    /**
     * GET /api/portfolio/cv/{id} – Get single CV.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CvResponse> get(@PathVariable Long id) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.get(user, id));
    }

    /**
     * PATCH /api/portfolio/cv/{id} – partial update.
     * Editable: title, html_content, is_public, prompt (raw text), design_preferences.
     * The server does NOT auto-regenerate prompt when design_preferences changes — user edits are preserved.
     */
    @Operation(summary = "CV 부분 수정",
            description = "title / html_content / is_public / is_favorite / prompt / design_preferences 부분 업데이트. "
                    + "design_preferences를 바꿔도 prompt는 자동 재생성되지 않습니다 (사용자 편집 보존). "
                    + "디자인 변경 + HTML 자동 생성은 POST /cv/{id}/generate-html 을 호출하세요.")
    @PatchMapping("/{id}")
    public ResponseEntity<CvResponse> patch(@PathVariable Long id, @javax.validation.Valid @RequestBody CvPatchRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.patch(user, id, request != null ? request : new CvPatchRequest()));
    }

    /**
     * POST /api/portfolio/cv/{id}/generate-html – Apply new design_preferences and render HTML via OpenAI.
     * Combines prompt regeneration + OpenAI call + html_content save in one round-trip.
     * Used by the FE "AI Customize" Apply button.
     */
    @Operation(summary = "AI Customize: design_preferences 적용 + HTML 즉시 생성 (OpenAI 호출)",
            description = "CV에 저장된 selections / 공고 정보에 새 design_preferences만 적용해 prompt를 재생성하고, "
                    + "이어서 OpenAI Chat Completions API를 호출해 HTML을 받아 저장합니다. 응답에서 prompt / design_preferences / "
                    + "html_content / model_used / tokens_used / last_generated_at 가 모두 갱신됩니다.\n\n"
                    + "사용자가 PATCH /{id}로 직접 손본 prompt 편집 내용은 사라지므로, FE에서 "
                    + "\"AI 커스터마이즈하면 직접 편집한 내용은 사라집니다\" 모달을 띄우는 것을 권장합니다.\n\n"
                    + "design_preferences를 null 또는 모두 빈 값으로 보내면 기본 파란 스타일(legacy)로 돌아갑니다.\n\n"
                    + "에러 매핑: 키 미설정 → 503 / OpenAI HTTP·파싱 에러 → 502 / 타임아웃 → 504. "
                    + "에러 발생 시에도 새 prompt와 design_preferences는 저장되어 있으므로 FE는 같은 endpoint로 재시도하면 됩니다.\n\n"
                    + "참고: selected_*_ids 컬럼이 추가되기 전에 만든 legacy CV(컬럼 값 null)는 selections 없이 재생성됩니다 — "
                    + "이 경우 FE는 차라리 POST /build-prompt를 다시 호출하세요.")
    @PostMapping("/{id}/generate-html")
    public ResponseEntity<CvResponse> generateHtml(@PathVariable Long id,
                                                   @javax.validation.Valid @RequestBody(required = false) CvRegeneratePromptRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.generateHtml(user, id, request));
    }

    /**
     * DELETE /api/portfolio/cv/{id} – Soft delete CV.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Users user = getCurrentUser();
        portfolioCvService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/portfolio/cv/{id}/restore – Restore a soft-deleted CV.
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<CvResponse> restore(@PathVariable Long id) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.restore(user, id));
    }

    private Users getCurrentUser() {
        String uniqueId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return authService.getLoginUser(uniqueId);
    }
}
