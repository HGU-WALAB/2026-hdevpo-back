package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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
    /** User design preferences chosen at build time (may be {@code null}). */
    private DesignPreferencesDto design_preferences;
    /** Selected portfolio_repo IDs persisted at build time. {@code null} for legacy CVs. */
    private List<Long> selected_repo_ids;
    /** Selected portfolio_mileage link IDs persisted at build time. {@code null} for legacy CVs. */
    private List<Long> selected_mileage_ids;
    /** Selected activity IDs persisted at build time. {@code null} for legacy CVs. */
    private List<Long> selected_activity_ids;
    /** {@code cv} or {@code archive} */
    private String mode;
    private String prompt;
    private String html_content;
    /** OpenAI model used for the most recent generate-html call ({@code null} if never generated). */
    private String model_used;
    /** Total tokens consumed by the most recent generate-html call. */
    private Integer tokens_used;
    /** Timestamp of the most recent successful generate-html call. */
    private LocalDateTime last_generated_at;
    /** Numeric share id; public HTML when is_public is true. */
    private String public_token;
    private boolean is_public;
    private boolean is_favorite;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
