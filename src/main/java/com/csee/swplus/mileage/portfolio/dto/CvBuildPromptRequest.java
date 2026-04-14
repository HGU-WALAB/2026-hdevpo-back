package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * Request body for POST /api/portfolio/cv/build-prompt.
 * Job info + selected portfolio item IDs to build the LLM prompt.
 * <p>
 * {@code mode}: {@code cv} (default) = recruiter-oriented prompt; {@code archive} = reflective self-assessment.
 * When {@code archive}, {@code job_posting} is described in the prompt as an interest / exploration area (field name unchanged).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CvBuildPromptRequest {

    /**
     * {@code cv} (default when omitted or null) or {@code archive}. Any other value → 400.
     */
    private String mode;

    /** 공고정보 (archive mode: treated as “interest area” in prompt text only). */
    private String job_posting;

    /** 지원 직무 */
    private String target_position;

    /** 추가 요청사항 */
    private String additional_notes;

    /**
     * Optional title. If blank, server sets "{@code target_position} · yyyy-MM-dd" (Asia/Seoul),
     * or "새 이력서 · yyyy-MM-dd" when 지원 직무도 비어 있음.
     */
    private String title;

    /** Selected mileage entry IDs (portfolio_mileage link id). */
    private List<Long> selected_mileage_ids = Collections.emptyList();

    /** Selected activity IDs. */
    private List<Long> selected_activity_ids = Collections.emptyList();

    /** Selected repo entry IDs (portfolio_repos id). */
    private List<Long> selected_repo_ids = Collections.emptyList();
}
