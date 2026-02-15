package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import lombok.*;

import javax.persistence.*;

/**
 * Portfolio-repository link: visibility toggle and custom titles.
 * Table: _sw_mileage_portfolio_repos
 */
@Entity
@Table(name = "_sw_mileage_portfolio_repos",
       uniqueConstraints = @UniqueConstraint(columnNames = { "portfolio_id", "repo_id" }),
       indexes = @Index(name = "idx_portfolio_visible", columnList = "portfolio_id, is_visible"))
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

    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private Boolean isVisible = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
