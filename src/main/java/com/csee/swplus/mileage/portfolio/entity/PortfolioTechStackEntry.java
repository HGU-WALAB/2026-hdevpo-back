package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import lombok.*;

import javax.persistence.*;

/**
 * One technology under a domain.
 * Table: _sw_mileage_portfolio_tech_stacks
 * {@code level} is 1–100 (single proficiency score; no separate {@code score} column).
 */
@Entity
@Table(name = "_sw_mileage_portfolio_tech_stacks",
        uniqueConstraints = @UniqueConstraint(name = "uq_techstack_snum_domain_name",
                columnNames = { "snum", "domain_id", "name" }),
        indexes = {
                @Index(name = "idx_techstack_snum", columnList = "snum"),
                @Index(name = "idx_techstack_domain_id", columnList = "domain_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioTechStackEntry extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "snum", nullable = false, length = 12)
    private String snum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "domain_id", nullable = false)
    private PortfolioDomain domain;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Proficiency 1–100. */
    @Column(name = "level", nullable = false)
    @Builder.Default
    private Integer level = 1;

}
