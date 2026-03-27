package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import lombok.*;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Tech stack domain grouping (e.g. Frontend, Backend).
 * Table: _sw_mileage_portfolio_domains
 */
@Entity
@Table(name = "_sw_mileage_portfolio_domains",
        uniqueConstraints = @UniqueConstraint(columnNames = { "snum", "name" }),
        indexes = @Index(name = "idx_portfolio_domains_snum", columnList = "snum"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioDomain extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "snum", nullable = false, length = 12)
    private String snum;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @OneToMany(mappedBy = "domain", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PortfolioTechStackEntry> techStacks = new ArrayList<>();
}
