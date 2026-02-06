package com.csee.swplus.mileage.setting.service;

import com.csee.swplus.mileage.setting.entity.Manager;
import com.csee.swplus.mileage.setting.entity.SwManagerSetting;
import com.csee.swplus.mileage.setting.repository.ManagerRepository;
import com.csee.swplus.mileage.setting.repository.SwManagerSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ManagerService {
    private final ManagerRepository managerRepository;
    private final SwManagerSettingRepository swManagerSettingRepository;

    public Manager getRegisterDate() {
        return managerRepository.findById(2L)
                .orElse(null);
    }

    public String getCurrentSemester() {
        String currentSemester = swManagerSettingRepository.findFirstByOrderByIdDesc()
                .map(SwManagerSetting::getCurrentSemester)
                .orElse("0000-00");

        return currentSemester;
    }

    /**
     * Returns contact info from manager setting (e.g. id=2).
     * Uses native query so it works even when contact_info column is missing
     * (returns "").
     */
    public String getContactInfo() {
        try {
            return managerRepository.findContactInfoById(2L)
                    .filter(s -> s != null && !s.isEmpty())
                    .orElse("");
        } catch (Exception e) {
            log.warn("Could not load contact_info (column may not exist): {}", e.getMessage());
            return "";
        }
    }

    /**
     * Returns MyPage announcement text from manager setting (e.g. id=2).
     * Uses a native query so it does not depend on other columns (contact_info,
     * reg_start/reg_end types, etc.). Returns "" if column/row is missing or any
     * error.
     */
    public String getMypageAnnouncement() {
        try {
            return managerRepository.findMypageAnnouncementById(2L)
                    .filter(s -> s != null && !s.isEmpty())
                    .orElse("");
        } catch (Exception e) {
            log.warn("Could not load mypage_announcement (add column if needed): {}", e.getMessage());
            return "";
        }
    }

    /**
     * Returns global maintenance flag from manager setting (id=2).
     * Uses SwManagerSetting so it only depends on maintenance_mode column.
     * If anything fails or column is null, returns false (no maintenance).
     */
    public boolean isMaintenanceMode() {
        try {
            return swManagerSettingRepository.findFirstByOrderByIdDesc()
                    .map(SwManagerSetting::getMaintenanceMode)
                    .map(v -> v != null && v == 1)
                    .orElse(false);
        } catch (Exception e) {
            log.warn("Could not load maintenance_mode (column may not exist): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Maintenance message text (nullable).
     */
    public String getMaintenanceMessage() {
        try {
            return managerRepository.findMaintenanceMessageById(2L)
                    .orElse("");
        } catch (Exception e) {
            log.warn("Could not load maintenance_message (column may not exist): {}", e.getMessage());
            return "";
        }
    }

    /**
     * Maintenance estimated time text (nullable).
     */
    public String getMaintenanceEta() {
        try {
            return managerRepository.findMaintenanceEtaById(2L)
                    .orElse("");
        } catch (Exception e) {
            log.warn("Could not load maintenance_eta (column may not exist): {}", e.getMessage());
            return "";
        }
    }

    /**
     * Whether given userId is allowed during maintenance (based on comma-separated IDs).
     */
    public boolean isUserAllowedDuringMaintenance(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        try {
            String raw = managerRepository.findMaintenanceAllowedIdsById(2L)
                    .orElse("");
            if (raw.isEmpty()) {
                return false;
            }
            String[] parts = raw.split(",");
            for (String part : parts) {
                if (userId.equals(part.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Could not load maintenance_allowed_ids (column may not exist): {}", e.getMessage());
            return false;
        }
    }
}
