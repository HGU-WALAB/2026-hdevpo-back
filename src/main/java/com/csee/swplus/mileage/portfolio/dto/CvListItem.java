package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * CV list item for list view (includes 직무 정보 for display).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvListItem {

    private Long id;
    private String title;
    /** 공고정보 */
    private String job_posting;
    /** 지원 직무 */
    private String target_position;
    /** 추가 요청사항 */
    private String additional_notes;
    /** User design preferences chosen at build time (may be {@code null}). */
    private DesignPreferencesDto design_preferences;
    /** {@code cv} or {@code archive} */
    private String mode;
    private String public_token;
    private boolean is_public;
    private boolean is_favorite;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
