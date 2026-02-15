package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import lombok.*;

import javax.persistence.*;

/**
 * Links portfolio to existing mileage record (_sw_mileage_record) and adds optional description.
 * Table: _sw_mileage_portfolio_mileage
 */
@Entity
@Table(name = "_sw_mileage_portfolio_mileage",
       uniqueConstraints = @UniqueConstraint(columnNames = { "portfolio_id", "mileage_id" }),
       indexes = @Index(name = "idx_portfolio_mileage_portfolio", columnList = "portfolio_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioMileageEntry extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "mileage_id", nullable = false)
    private Long mileageId;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
