package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import com.csee.swplus.mileage.portfolio.converter.TeamCompositionJsonConverter;
import com.csee.swplus.mileage.portfolio.dto.TeamRoleDto;
import lombok.*;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Portfolio-repository link: visibility toggle and custom titles.
 * Table: _sw_mileage_portfolio_repos
 */
@Entity
@Table(name = "_sw_mileage_portfolio_repos", uniqueConstraints = @UniqueConstraint(columnNames = { "portfolio_id",
        "repo_id" }), indexes = @Index(name = "idx_portfolio_visible", columnList = "portfolio_id, is_visible"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioRepoEntry extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "custom_title", length = 255)
    private String customTitle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private Boolean isVisible = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * JSON-encoded {@code [{role, count}, ...]}; never null at the DB layer
     * ({@code "[]"} when empty).
     */
    @Convert(converter = TeamCompositionJsonConverter.class)
    @Column(name = "team_composition", columnDefinition = "TEXT")
    @Builder.Default
    private List<TeamRoleDto> teamComposition = new ArrayList<>();

    /**
     * Current user's role label on this repo (free text, e.g. "Frontend
     * Developer").
     */
    @Column(name = "my_role_role", length = 100)
    private String myRoleRole;

    /**
     * Current user's contribution share on this repo, 0–100 (validated at the API
     * layer).
     */
    @Column(name = "my_role_contribution_percent")
    private Integer myRoleContributionPercent;

    /**
     * Free-text key contributions on this repo (multi-line; capped at the API
     * layer).
     */
    @Column(name = "key_contributions", columnDefinition = "TEXT")
    private String keyContributions;
}
