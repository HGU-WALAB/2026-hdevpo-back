package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Request body for POST /api/portfolio/cv.
 * Creates a new CV after user pastes LLM-generated HTML.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CvCreateRequest {

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    /** 공고정보 */
    private String job_posting;

    /** 지원 직무 */
    private String target_position;

    /** 추가 요청사항 */
    private String additional_notes;

    /** Full prompt sent to LLM (stored for reference). */
    @NotNull
    private String prompt;

    /** LLM-generated HTML content. */
    @NotNull
    private String html_content;
}
