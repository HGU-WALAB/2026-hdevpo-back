package com.csee.swplus.mileage.portfolio.controller;

import com.csee.swplus.mileage.auth.service.AuthService;
import com.csee.swplus.mileage.portfolio.dto.UserInfoPatchRequest;
import com.csee.swplus.mileage.portfolio.dto.UserInfoResponse;
import com.csee.swplus.mileage.portfolio.service.PortfolioService;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * Portfolio "내 정보 모아보기" API.
 * Base path: /api/portfolio
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final AuthService authService;
    private final PortfolioService portfolioService;

    /**
     * GET /api/portfolio/user-info – 기본 정보 (이름, 학교, 전공, 학년, 학기, 소개글).
     */
    @GetMapping("/user-info")
    public ResponseEntity<UserInfoResponse> getUserInfo() {
        Users user = getCurrentUser();
        UserInfoResponse body = portfolioService.getUserInfo(user);
        return ResponseEntity.ok(body);
    }

    /**
     * PATCH /api/portfolio/user-info – 소개글(bio) 수정.
     * Body: { "bio": "안녕하세요 ..." }
     */
    @PatchMapping("/user-info")
    public ResponseEntity<UserInfoResponse> patchUserInfo(@Valid @RequestBody UserInfoPatchRequest request) {
        Users user = getCurrentUser();
        UserInfoResponse body = portfolioService.updateBio(user, request.getBio());
        return ResponseEntity.ok(body);
    }

    private Users getCurrentUser() {
        String uniqueId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return authService.getLoginUser(uniqueId);
    }
}
