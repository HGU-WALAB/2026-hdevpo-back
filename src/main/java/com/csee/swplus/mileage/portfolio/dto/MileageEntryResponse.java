package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One linked mileage in GET list or POST/PUT response (id = portfolio_mileage link id).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MileageEntryResponse {

    private Long id;
    private Long mileage_id;
    private String additional_info;
    private Integer display_order;

    // 원본 마일리지(_sw_mileage_record)에서 가져오는 읽기 전용 정보들
    private Integer subitemId;
    private Integer categoryId;
    private String semester;
    private String description1;
}
