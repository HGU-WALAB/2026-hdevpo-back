package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import com.csee.swplus.mileage.portfolio.converter.DesignPreferencesJsonConverter;
import com.csee.swplus.mileage.portfolio.converter.LongListJsonConverter;
import com.csee.swplus.mileage.portfolio.dto.DesignPreferencesDto;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * User-created CV/Resume with job info, prompt, and LLM-generated HTML.
 * Table: _sw_mileage_portfolio_cv
 */
@Entity
@Table(name = "_sw_mileage_portfolio_cv", indexes = @Index(name = "idx_portfolio_cv_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioCv extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "job_posting", columnDefinition = "TEXT")
    private String jobPosting;

    @Column(name = "target_position", length = 500)
    private String targetPosition;

    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    /**
     * User design choices for the CV prompt step 2 ({@code [design_preferences]} block).
     * JSON-encoded in {@code design_preferences TEXT}; {@code null} when the user did not choose anything.
     */
    @Convert(converter = DesignPreferencesJsonConverter.class)
    @Column(name = "design_preferences", columnDefinition = "TEXT")
    private DesignPreferencesDto designPreferences;

    /**
     * Selected portfolio repo IDs at build time. Persisted so {@code POST /cv/{id}/regenerate-prompt}
     * can rebuild the prompt without the FE re-sending selections.
     * {@code null} for legacy CVs created before this column existed.
     */
    @Convert(converter = LongListJsonConverter.class)
    @Column(name = "selected_repo_ids", columnDefinition = "TEXT")
    private List<Long> selectedRepoIds;

    /** Selected mileage link IDs at build time. {@code null} for legacy CVs. */
    @Convert(converter = LongListJsonConverter.class)
    @Column(name = "selected_mileage_ids", columnDefinition = "TEXT")
    private List<Long> selectedMileageIds;

    /** Selected activity IDs at build time. {@code null} for legacy CVs. */
    @Convert(converter = LongListJsonConverter.class)
    @Column(name = "selected_activity_ids", columnDefinition = "TEXT")
    private List<Long> selectedActivityIds;

    /** Stored as {@code cv} or {@code archive} (see {@code CvPromptMode}). */
    @Column(name = "mode", nullable = false, length = 16)
    @Builder.Default
    private String mode = "cv";

    @Column(name = "prompt", columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "html_content", columnDefinition = "LONGTEXT")
    private String htmlContent;

    /** Numeric id for public URL (8–12 digits); unique when set. */
    @Column(name = "public_token", length = 12, unique = true)
    private String publicToken;

    /** When true, HTML is served at GET /api/portfolio/share/cv/{public_token}/html */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    /** Soft delete flag. Deleted CVs are hidden from default list/get/share. */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /** When soft-deleted, deletion timestamp; null otherwise. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
