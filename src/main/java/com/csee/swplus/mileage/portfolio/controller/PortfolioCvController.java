package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.*;
import com.csee.swplus.mileage.portfolio.service.PortfolioCvService;
import com.csee.swplus.mileage.user.entity.Users;
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
     * Returns prompt + cv_id. User copies prompt to LLM, pastes HTML, then PATCH /cv/{id} with html_content.
     */
    @PostMapping("/build-prompt")
    public ResponseEntity<CvBuildPromptResponse> buildPrompt(@RequestBody CvBuildPromptRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.buildPrompt(user, request != null ? request : new CvBuildPromptRequest()));
    }

    /**
     * GET /api/portfolio/cv – List all CVs for the user.
     */
    @GetMapping
    public ResponseEntity<CvListResponse> list() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.list(user));
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
     * PATCH /api/portfolio/cv/{id} – Update title and/or html_content only.
     * Body: { title, html_content } (optional fields)
     */
    @PatchMapping("/{id}")
    public ResponseEntity<CvResponse> patch(@PathVariable Long id, @RequestBody CvPatchRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioCvService.patch(user, id, request != null ? request : new CvPatchRequest()));
    }

    /**
     * DELETE /api/portfolio/cv/{id} – Delete CV.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Users user = getCurrentUser();
        portfolioCvService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    private Users getCurrentUser() {
        String uniqueId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return authService.getLoginUser(uniqueId);
    }
}
