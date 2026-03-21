package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Single activity in GET list or POST/PUT response.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDate start_date;
    private LocalDate end_date;
    /** Category: "activity", "project", "certificate", "camp", "other", etc. */
    private String category;
    private Integer display_order;
}
