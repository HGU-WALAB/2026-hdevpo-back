package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Full CV response (single CV get).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvResponse {

    private Long id;
    private String title;
    private String job_posting;
    private String target_position;
    private String additional_notes;
    /** {@code cv} or {@code archive} */
    private String mode;
    private String prompt;
    private String html_content;
    /** Numeric share id; public HTML when is_public is true. */
    private String public_token;
    private boolean is_public;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
