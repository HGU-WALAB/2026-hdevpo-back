package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/portfolio/settings – 섹션 순서 응답.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsResponse {

    private List<String> section_order;
}
