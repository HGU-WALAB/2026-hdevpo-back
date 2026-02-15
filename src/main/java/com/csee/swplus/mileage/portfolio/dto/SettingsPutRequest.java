package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * PUT /api/portfolio/settings – 섹션 레이아웃 순서 (유저 정보는 상단 고정).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SettingsPutRequest {

    private List<String> section_order;
}
