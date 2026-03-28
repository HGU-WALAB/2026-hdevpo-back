package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageLinkRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageListResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageUpdateRequest;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.RepoPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.RepositoriesResponse;
import com.csee.swplus.mileage.portfolio.dto.SettingsPutRequest;
import com.csee.swplus.mileage.portfolio.dto.SettingsResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackPutRequest;
import com.csee.swplus.mileage.portfolio.dto.TechStackResponse;
import com.csee.swplus.mileage.portfolio.dto.UserInfoPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.UserInfoResponse;
import com.csee.swplus.mileage.portfolio.service.PortfolioHtmlExportService;
import com.csee.swplus.mileage.portfolio.service.PortfolioService;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/**
 * Portfolio "내 정보 모아보기" API (프로필, 기술스택, 레포, 마일리지, 설정, 내보내기).
 * 활동(activities)은 {@link PortfolioActivitiesController} 참고.
 * Base path: /api/portfolio
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Portfolio", description = "내 정보 모아보기 — 프로필, 기술스택, 레포, 마일리지, 설정, 내보내기. "
        + "활동(activities) API는 Swagger에서 「Portfolio — Activities」 그룹을 참고하세요.")
public class PortfolioController {

    private final AuthService authService;
    private final PortfolioService portfolioService;
    private final PortfolioHtmlExportService htmlExportService;

    @Value("${file.portfolio-profile-upload-dir:${file.profile-upload-dir:./uploads/profile}}")
    private String profileUploadDir;

    /**
     * GET /api/portfolio/user-info – 기본 정보 (이름, 학교, 전공, 학년, 학기, 소개글, 프로필 이미지).
     */
    @GetMapping("/user-info")
    @Operation(summary = "기본 프로필 조회")
    public ResponseEntity<UserInfoResponse> getUserInfo() {
        Users user = getCurrentUser();
        UserInfoResponse body = portfolioService.getUserInfo(user);
        return ResponseEntity.ok(body);
    }

    /**
     * PATCH /api/portfolio/user-info – 소개글(bio) 및 프로필 이미지 수정 (multipart/form-data).
     * Body: bio (text, optional), profile_image (file, optional)
     */
    @PatchMapping(value = "/user-info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로필 수정 (multipart)", description = "bio, profile_image 파일")
    public ResponseEntity<UserInfoResponse> patchUserInfoMultipart(
            @RequestParam(value = "bio", required = false) String bio,
            @RequestPart(value = "profile_image", required = false) MultipartFile profileImage) {
        Users user = getCurrentUser();
        String profileImageUrl = null;
        
        // Handle file upload
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                // Get current portfolio to check for existing image
                com.csee.swplus.mileage.portfolio.entity.Portfolio currentPortfolio = portfolioService.getOrCreatePortfolio(user);
                String oldImageUrl = currentPortfolio.getProfileImageUrl();
                
                // Delete old image if exists
                if (oldImageUrl != null && !oldImageUrl.isEmpty()) {
                    try {
                        Path oldImagePath = Paths.get(profileUploadDir).resolve(oldImageUrl);
                        Files.deleteIfExists(oldImagePath);
                        log.info("Deleted old profile image: {}", oldImageUrl);
                    } catch (IOException e) {
                        log.warn("Failed to delete old profile image: {}", oldImageUrl, e);
                    }
                }
                
                // Ensure upload directory exists
                Path uploadPath = Paths.get(profileUploadDir);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                
                // Generate unique filename
                String originalFilename = profileImage.getOriginalFilename();
                String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                    : "";
                String uniqueFilename = UUID.randomUUID().toString() + extension;
                
                // Save file
                Path targetLocation = uploadPath.resolve(uniqueFilename);
                Files.copy(profileImage.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                
                profileImageUrl = uniqueFilename;
                log.info("Profile image saved: {}", uniqueFilename);
            } catch (IOException e) {
                log.error("Failed to save profile image", e);
                throw new RuntimeException("프로필 이미지 저장 중 오류가 발생했습니다.");
            }
        }
        
        UserInfoResponse body = portfolioService.updateBio(user, bio, profileImageUrl);
        return ResponseEntity.ok(body);
    }

    /**
     * PATCH /api/portfolio/user-info – 소개글(bio) 및 프로필 이미지 URL 수정 (application/json).
     * Body: { "bio": "...", "profile_image_url": "..." }
     * Use this for updating bio only or setting/clearing profile_image_url without file upload.
     */
    @PatchMapping(value = "/user-info", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "프로필 수정 (JSON)", description = "bio, profile_image_url")
    public ResponseEntity<UserInfoResponse> patchUserInfoJson(@Valid @RequestBody UserInfoPatchRequest request) {
        Users user = getCurrentUser();
        UserInfoResponse body = portfolioService.updateBio(user, request.getBio(), request.getProfile_image_url());
        return ResponseEntity.ok(body);
    }

    /**
     * GET /api/portfolio/user-info/image/{filename} – 프로필 이미지 조회.
     */
    @GetMapping("/user-info/image/{filename}")
    @Operation(summary = "프로필 이미지 파일 조회")
    public ResponseEntity<Resource> getProfileImage(@PathVariable String filename) {
        try {
            Path filePath = Paths.get(profileUploadDir).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IOException e) {
            log.error("Failed to load profile image: {}", filename, e);
            return ResponseEntity.badRequest().build();
        }
    }

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
     * GET /api/portfolio/repositories – GitHub 레포 목록 + (선택된 레포에 한해) 커스텀 설정 정보.
     * Optional: ?page=1&per_page=30 | ?selected_only=true | ?visible_only=true |
     * ?sort=updated|created|pushed|full_name | ?visibility=all|public|private |
     * ?affiliation=owner,collaborator,organization_member (requires stored token for private/org).
     */
    @GetMapping("/repositories")
    @Operation(summary = "GitHub 레포 목록", description = "페이지·필터·정렬 쿼리 지원")
    public ResponseEntity<RepositoriesResponse> getRepositories(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "per_page", required = false) Integer perPage,
            @RequestParam(value = "selected_only", required = false) Boolean selectedOnly,
            @RequestParam(value = "visible_only", required = false) Boolean visibleOnly,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "affiliation", required = false) String affiliation) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getRepositories(user, page, perPage, selectedOnly, visibleOnly, sort, visibility, affiliation));
    }

    /**
     * PUT /api/portfolio/repositories – 전체 목록 교체 (batch sync).
     * Body: [ { "repo_id": 123, "custom_title": "Project A", "is_visible": true }, ... ]
     */
    @PutMapping("/repositories")
    @Operation(summary = "레포 표시 설정 일괄 동기화")
    public ResponseEntity<RepositoriesResponse> putRepositories(@Valid @RequestBody List<RepoEntryRequest> request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putRepositories(user, request));
    }

    /**
     * PATCH /api/portfolio/repositories/{id} – 단일 레포 엔트리 일부 수정.
     * Body 예시: { "custom_title": "New title", "is_visible": true }
     */
    @PatchMapping("/repositories/{id}")
    @Operation(summary = "단일 레포 설정 수정")
    public ResponseEntity<RepoEntryResponse> patchRepository(
            @PathVariable Long id,
            @RequestBody RepoPatchRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(
                portfolioService.patchRepository(user, id, request != null ? request : new RepoPatchRequest()));
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
