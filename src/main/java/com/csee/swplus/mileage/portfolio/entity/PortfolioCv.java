package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import com.csee.swplus.mileage.user.entity.Users;
import lombok.*;

import javax.persistence.*;

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
}
