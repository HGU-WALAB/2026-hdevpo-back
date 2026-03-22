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
    private String prompt;
    private String html_content;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
