package com.csee.swplus.mileage.setting.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceResponse {
    private boolean maintenanceMode;
    private String message;
    private String estimatedTime;
    private boolean isAllowedUser;
}
