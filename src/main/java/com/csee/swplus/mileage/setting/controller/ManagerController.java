package com.csee.swplus.mileage.setting.controller;

import com.csee.swplus.mileage.auth.util.JwtUtil;
import com.csee.swplus.mileage.setting.dto.AnnouncementResponse;
import com.csee.swplus.mileage.setting.dto.ContactResponse;
import com.csee.swplus.mileage.setting.dto.MaintenanceResponse;
import com.csee.swplus.mileage.setting.dto.ManagerResponse;
import com.csee.swplus.mileage.setting.entity.SwManagerSetting;
import com.csee.swplus.mileage.setting.repository.ManagerRepository;
import com.csee.swplus.mileage.setting.repository.SwManagerSettingRepository;
import com.csee.swplus.mileage.setting.entity.Manager;
import com.csee.swplus.mileage.setting.service.ManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mileage")
@RequiredArgsConstructor
@Slf4j
public class ManagerController {
    private final ManagerService managerService;
    private final ManagerRepository managerRepository;
    private final SwManagerSettingRepository swManagerSettingRepository;

    @Value("${custom.jwt.secret}")
    private String jwtSecret;

    @GetMapping("/apply")
    public ManagerResponse getMileageSetting() {
        Manager manager = managerService.getRegisterDate();

        if (manager == null) {
            log.error("Manager 정보가 없습니다.");
            return null;
        }

        return new ManagerResponse(manager.getRegStart(), manager.getRegEnd());
    }

    @GetMapping("/contact")
    public ResponseEntity<ContactResponse> getContact() {
        String contactInfo = managerService.getContactInfo();
        return ResponseEntity.ok(new ContactResponse(contactInfo));
    }

    /**
     * MyPage announcement (e.g. "학부와 전공 정보를 확인한 후, 장학금 신청 대상인 경우에만 신청하세요.").
     * Stored in _sw_manager_setting.mypage_announcement.
     */
    @GetMapping("/announcement")
    public ResponseEntity<AnnouncementResponse> getAnnouncement() {
        String announcement = managerService.getMypageAnnouncement();
        return ResponseEntity.ok(new AnnouncementResponse(announcement));
    }

    /**
     * Maintenance status endpoint.
     * Returns:
     * {
     *   "maintenanceMode": boolean,
     *   "message": string,
     *   "estimatedTime": string,
     *   "isAllowedUser": boolean
     * }
     *
     * Frontend can use this to decide whether to show a maintenance page.
     */
    @GetMapping("/maintenance")
    public ResponseEntity<MaintenanceResponse> getMaintenance(HttpServletRequest request) {
        // Use DB-driven evaluation directly to avoid any mismatch (0/1 from DB)
        Integer active = managerRepository.isMaintenanceActive().orElse(0);
        boolean maintenanceMode = active != null && active != 0;
        String message = managerService.getMaintenanceMessage();
        String eta = managerService.getMaintenanceEta();

        // Try to determine if current user is allowed during maintenance
        String currentUserId = extractUserIdFromAccessToken(request);
        boolean isAllowedUser = managerService.isUserAllowedDuringMaintenance(currentUserId);

        MaintenanceResponse body = new MaintenanceResponse(
                maintenanceMode,
                message,
                eta,
                isAllowedUser
        );
        return ResponseEntity.ok(body);
    }

    /**
     * Debug endpoint to inspect raw maintenance values that the backend actually sees.
     * Helps verify DB wiring without server log access.
     */
    @GetMapping("/maintenance/debug")
    public ResponseEntity<Map<String, Object>> getMaintenanceDebug() {
        Map<String, Object> debug = new HashMap<>();

        // 1) Computed value used by /maintenance
        boolean computed = managerService.isMaintenanceMode();
        debug.put("maintenanceMode", computed);

        // 2) Raw row (id = 2) as seen by JPA
        SwManagerSetting row = swManagerSettingRepository.findById(2L).orElse(null);
        if (row == null) {
            debug.put("rowExists", false);
        } else {
            debug.put("rowExists", true);
            debug.put("rowId", 2);
            debug.put("maintenance_mode", row.getMaintenanceMode());
            debug.put("read_start", row.getReadStart());
            debug.put("read_end", row.getReadEnd());
        }

        // 3) Direct DB evaluation via isMaintenanceActive() (0/1)
        debug.put("dbActive", managerRepository.isMaintenanceActive().orElse(0));

        return ResponseEntity.ok(debug);
    }

    private String extractUserIdFromAccessToken(HttpServletRequest request) {
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            String accessToken = null;
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    accessToken = cookie.getValue();
                    break;
                }
            }
            if (accessToken == null || accessToken.isEmpty()) {
                return null;
            }
            Key signingKey = JwtUtil.getSigningKey(jwtSecret);
            return JwtUtil.getUserId(accessToken, signingKey);
        } catch (Exception e) {
            log.warn("Failed to extract userId from accessToken for maintenance check: {}", e.getMessage());
            return null;
        }
    }
}

