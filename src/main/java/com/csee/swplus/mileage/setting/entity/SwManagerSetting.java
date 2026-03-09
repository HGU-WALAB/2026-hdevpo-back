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

    @Column(name = "current_semester", length = 20)
    private String currentSemester;

    @Column(name = "maintenance_mode")
    private Integer maintenanceMode;

    @Column(name = "read_start")
    private LocalDateTime readStart;

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