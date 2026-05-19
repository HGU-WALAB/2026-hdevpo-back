package com.csee.swplus.mileage.portfolio.entity;

import com.csee.swplus.mileage.base.entity.BaseTime;
import lombok.*;

import com.csee.swplus.mileage.portfolio.converter.StringListJsonConverter;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    /** Organizer / host (주최). */
    @Column(name = "host", length = 255)
    private String host;

    /** User role in the activity (역할). */
    @Column(name = "role", length = 500)
    private String role;

    /** Outcomes summary (성과 및 결과). */
    @Column(name = "achievements", columnDefinition = "TEXT")
    private String achievements;

    /** Detailed outcome description (성과 설명). */
    @Column(name = "achievements_detail", columnDefinition = "TEXT")
    private String achievementsDetail;

    /** Optional link (e.g. club site, certificate page). */
    @Column(name = "url", length = 2048)
    private String url;

    /** Tags for future filter/sort; stored as JSON array in DB. */
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "tags", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

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
