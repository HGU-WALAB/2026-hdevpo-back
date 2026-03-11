package com.csee.swplus.mileage.setting.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "_sw_manager_setting")
public class SwManagerSetting {
    @Id
    private Long id;

    // 학사 마일리지 시스템에서 사용하는 현재 학기 (예: "2025-1")
    @Column(name = "current_semester", length = 20)
    private String currentSemester;

    // 전역 점검 모드 플래그 (0 = 정상, 1 = 점검)
    @Column(name = "maintenance_mode")
    private Integer maintenanceMode;

    // 점검 노출 시작 시각 (이 시각 이후부터 점검으로 판단)
    @Column(name = "read_start")
    private LocalDateTime readStart;

    // 점검 노출 종료 시각 (이 시각 이후에는 다시 정상으로 판단)
    @Column(name = "read_end")
    private LocalDateTime readEnd;

    public String getCurrentSemester() {
        return currentSemester;
    }

    public void setCurrentSemester(String currentSemester) {
        this.currentSemester = currentSemester;
    }

    public Integer getMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(Integer maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    public LocalDateTime getReadStart() {
        return readStart;
    }

    public void setReadStart(LocalDateTime readStart) {
        this.readStart = readStart;
    }

    public LocalDateTime getReadEnd() {
        return readEnd;
    }

    public void setReadEnd(LocalDateTime readEnd) {
        this.readEnd = readEnd;
    }
}