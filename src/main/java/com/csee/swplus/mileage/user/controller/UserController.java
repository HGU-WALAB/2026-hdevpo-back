package com.csee.swplus.mileage.user.controller;

import lombok.RequiredArgsConstructor;
import com.csee.swplus.mileage.user.controller.response.UserResponse;
import com.csee.swplus.mileage.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mileage/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("")
    public ResponseEntity<UserResponse> getUserInfo() {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(userService.getUserInfo(currentUserId));
    }

//    @PatchMapping("/{studentId}")
//    public ResponseEntity<UserResponse> updateUserInfo(@PathVariable String uniqueId, @RequestBody UserRequest updateRequest) {
//        UserResponse userResponse = userService.updateUserInfo(uniqueId, updateRequest);
//        return ResponseEntity.ok(userResponse);
//    }
}