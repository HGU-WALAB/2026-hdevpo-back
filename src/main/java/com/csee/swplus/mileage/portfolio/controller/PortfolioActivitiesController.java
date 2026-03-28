package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.ActivitiesResponse;
import com.csee.swplus.mileage.portfolio.dto.ActivityPatchItemRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityRequest;
import com.csee.swplus.mileage.portfolio.dto.ActivityResponse;
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
 * Activities API — grouped in Swagger separately from the rest of portfolio (same pattern as {@link PortfolioCvController}).
 * Base path: /api/portfolio/activities
 */
@RestController
@RequestMapping("/api/portfolio/activities")
@RequiredArgsConstructor
@Tag(name = "Portfolio — Activities", description = "활동·경험 (목록/필터, CRUD, 선택 url·tags)")
public class PortfolioActivitiesController {

    private final AuthService authService;
    private final PortfolioService portfolioService;

    @GetMapping
    @Operation(summary = "활동 목록", description = "선택: category 반복 쿼리로 필터 (미지정 시 전체)")
    public ResponseEntity<ActivitiesResponse> getActivities(
            @RequestParam(value = "category", required = false) List<String> categories) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.getActivities(user, categories));
    }

    @PostMapping
    @Operation(summary = "활동 추가", description = "생성된 id로 이후 PUT/PATCH/DELETE")
    public ResponseEntity<ActivityResponse> postActivity(@Valid @RequestBody ActivityRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.createActivity(user, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "활동 전체 수정", description = "PUT — 본문 필드 전체 덮어쓰기")
    public ResponseEntity<ActivityResponse> putActivity(@PathVariable Long id, @Valid @RequestBody ActivityRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.updateActivity(user, id, request));
    }

    /** Path-variable route registered before parameterless PATCH so /activities/{id} is not ambiguous. */
    @PatchMapping("/{id}")
    @Operation(summary = "활동 일부 수정", description = "PATCH — null이 아닌 필드만 반영 (url/tags 포함)")
    public ResponseEntity<ActivityResponse> patchActivity(@PathVariable Long id, @RequestBody ActivityPatchRequest request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.patchActivity(user, id, request != null ? request : new ActivityPatchRequest()));
    }

    @PatchMapping
    @Operation(summary = "활동 배치 일부 수정", description = "PATCH — [{ id, ...필드 }] 각 id별로 보낸 필드만 반영")
    public ResponseEntity<ActivitiesResponse> patchActivities(@RequestBody List<ActivityPatchItemRequest> request) {
        Users user = getCurrentUser();
        return ResponseEntity.ok(portfolioService.patchActivities(user, request != null ? request : java.util.Collections.emptyList()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "활동 삭제")
    public ResponseEntity<Void> deleteActivity(@PathVariable Long id) {
        Users user = getCurrentUser();
        portfolioService.deleteActivity(user, id);
        return ResponseEntity.noContent().build();
    }

    private Users getCurrentUser() {
        String uniqueId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return authService.getLoginUser(uniqueId);
    }
}
