package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * GET /api/portfolio/user-info – 기본 정보 응답 (학교 정보는 수정 불가, bio만 편집 가능).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    private String name;
    private String department;
    private String major1;
    private String major2;
    private Integer grade;
    private Integer semester;
    private String bio;
}
