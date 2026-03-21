package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDate;

/**
 * User-defined activity (e.g. club, role).
 * Table: _sw_mileage_portfolio_activities
 */
@Entity
@Table(name = "_sw_mileage_portfolio_activities", indexes = @Index(name = "idx_portfolio_activities_portfolio", columnList = "portfolio_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioActivity extends BaseTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Category: "activity", "project", "certificate", "camp", "other", etc. */
    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
