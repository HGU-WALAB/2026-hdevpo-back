package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.ActivitiesResponse;
import com.csee.swplus.mileage.portfolio.dto.ActivityPatchItemRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageEntryResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageLinkRequest;
import com.csee.swplus.mileage.portfolio.dto.MileageListResponse;
import com.csee.swplus.mileage.portfolio.dto.MileageUpdateRequest;
import com.csee.swplus.mileage.portfolio.dto.RepoEntryRequest;
import com.csee.swplus.mileage.portfolio.dto.RepositoriesResponse;
import com.csee.swplus.mileage.portfolio.dto.SettingsPutRequest;
import com.csee.swplus.mileage.portfolio.dto.SettingsResponse;
import com.csee.swplus.mileage.portfolio.dto.TechStackPutRequest;
import com.csee.swplus.mileage.portfolio.dto.TechStackResponse;
import com.csee.swplus.mileage.portfolio.dto.UserInfoPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.UserInfoResponse;
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
 * Portfolio "내 정보 모아보기" API.
 * Base path: /api/portfolio
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
@Slf4j
public class PortfolioController {

    private final AuthService authService;
    private final PortfolioService portfolioService;

    @Value("${file.portfolio-profile-upload-dir:${file.profile-upload-dir:./uploads/profile}}")
    private String profileUploadDir;

    /**
     * GET /api/portfolio/user-info – 기본 정보 (이름, 학교, 전공, 학년, 학기, 소개글, 프로필 이미지).
     */
    @GetMapping("/user-info")
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
    public ResponseEntity<UserInfoResponse> patchUserInfoJson(@Valid @RequestBody UserInfoPatchRequest request) {
        Users user = getCurrentUser();
        UserInfoResponse body = portfolioService.updateBio(user, request.getBio(), request.getProfile_image_url());
        return ResponseEntity.ok(body);
    }

    /**
     * GET /api/portfolio/user-info/image/{filename} – 프로필 이미지 조회.
     */
    @GetMapping("/user-info/image/{filename}")
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
    public ResponseEntity<TechStackResponse> getTechStack() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getTechStack(user));
    }

    /**
     * PUT /api/portfolio/tech-stack – 기술 스택 전체 교체.
     * Body: { "tech_stack": ["Java", "Spring Boot", "Docker"] }
     */
    @PutMapping("/tech-stack")
    public ResponseEntity<TechStackResponse> putTechStack(@Valid @RequestBody TechStackPutRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putTechStack(user, request.getTech_stack()));
    }

    /**
     * GET /api/portfolio/repositories – 노출 토글 + 커스텀 제목 목록.
     */
    @GetMapping("/repositories")
    public ResponseEntity<RepositoriesResponse> getRepositories() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getRepositories(user));
    }

    /**
     * PUT /api/portfolio/repositories – 전체 목록 교체 (batch sync).
     * Body: [ { "repo_id": 123, "custom_title": "Project A", "is_visible": true }, ... ]
     */
    @PutMapping("/repositories")
    public ResponseEntity<RepositoriesResponse> putRepositories(@Valid @RequestBody List<RepoEntryRequest> request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putRepositories(user, request));
    }

    /**
     * GET /api/portfolio/activities – 활동 목록. Optional: ?category=1&category=2 to filter by category (default: full list).
     */
    @GetMapping("/activities")
    public ResponseEntity<ActivitiesResponse> getActivities(
            @RequestParam(value = "category", required = false) List<Integer> categories) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getActivities(user, categories));
    }

    /**
     * POST /api/portfolio/activities – 활동 추가 (반환 id로 이후 PUT).
     * Body: { "title": "...", "description": "...", "start_date": "2024-01-01", "end_date": "2024-06-30" }
     */
    @PostMapping("/activities")
    public ResponseEntity<ActivityResponse> postActivity(@Valid @RequestBody ActivityRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.createActivity(user, request));
    }

    /**
     * PUT /api/portfolio/activities/{id} – 활동 전체 수정.
     */
    @PutMapping("/activities/{id}")
    public ResponseEntity<ActivityResponse> putActivity(@PathVariable Long id, @Valid @RequestBody ActivityRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.updateActivity(user, id, request));
    }

    /**
     * PATCH /api/portfolio/activities/{id} – 활동 일부 수정 (보내진 필드만 반영). Body: { "category": 2 }, { "title": "..." }, etc.
     */
    @PatchMapping("/activities/{id}")
    public ResponseEntity<ActivityResponse> patchActivity(@PathVariable Long id, @RequestBody ActivityPatchRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.patchActivity(user, id, request != null ? request : new ActivityPatchRequest()));
    }

    /**
     * PATCH /api/portfolio/activities – 전체 목록 일부 수정. Body: [ { "id": 1, "category": 2 }, { "id": 2, "title": "..." }, ... ]
     * 각 항목은 id로 식별되며, 보내진 필드만 반영됩니다.
     */
    @PatchMapping("/activities")
    public ResponseEntity<ActivitiesResponse> patchActivities(@RequestBody List<ActivityPatchItemRequest> request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.patchActivities(user, request != null ? request : java.util.Collections.emptyList()));
    }

    /**
     * DELETE /api/portfolio/activities/{id} – 활동 삭제.
     */
    @DeleteMapping("/activities/{id}")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        Users user = getCurrentUser();
        portfolioService.deleteActivity(user, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/portfolio/mileage – 연결된 마일리지 목록.
     */
    @GetMapping("/mileage")
    public ResponseEntity<MileageListResponse> getMileage() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getMileageList(user));
    }

    /**
     * PUT /api/portfolio/mileage – 전체 목록 교체 (repositories와 동일 패턴).
     * Body: [ { "mileage_id": 789, "additional_info": "설명" }, ... ]
     */
    @PutMapping("/mileage")
    public ResponseEntity<MileageListResponse> putMileage(@Valid @RequestBody List<MileageEntryRequest> request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putMileageList(user, request != null ? request : java.util.Collections.emptyList()));
    }

    /**
     * POST /api/portfolio/mileage – 기존 마일리지 연결.
     * Body: { "mileage_id": 789, "additional_info": "상세 설명" }
     */
    @PostMapping("/mileage")
    public ResponseEntity<MileageEntryResponse> postMileage(@Valid @RequestBody MileageLinkRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.linkMileage(user, request));
    }

    /**
     * PUT /api/portfolio/mileage/{id} – 추가 설명만 수정 (id = portfolio_mileage link id).
     * Body: { "additional_info": "내용 수정" }
     */
    @PutMapping("/mileage/{id}")
    public ResponseEntity<MileageEntryResponse> putMileage(@PathVariable Long id, @RequestBody MileageUpdateRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.updateMileageEntry(user, id, request != null ? request.getAdditional_info() : null));
    }

    /**
     * DELETE /api/portfolio/mileage/{id} – 연결 해제 (원본 마일리지는 삭제하지 않음).
     */
    @DeleteMapping("/mileage/{id}")
    public ResponseEntity<Void> deleteMileage(@PathVariable Long id) {
        Users user = getCurrentUser();
        portfolioService.unlinkMileage(user, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/portfolio/settings – 섹션 순서 (유저 정보는 상단 고정).
     */
    @GetMapping("/settings")
    public ResponseEntity<SettingsResponse> getSettings() {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getSettings(user));
    }

    /**
     * PUT /api/portfolio/settings – 섹션 레이아웃 순서 변경.
     * Body: { "section_order": ["tech", "repo", "activities", "mileage"] }
     */
    @PutMapping("/settings")
    public ResponseEntity<SettingsResponse> putSettings(@RequestBody SettingsPutRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.putSettings(user, request != null ? request.getSection_order() : null));
    }

    private Users getCurrentUser() {
        String uniqueId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return authService.getLoginUser(uniqueId);
    }
}
