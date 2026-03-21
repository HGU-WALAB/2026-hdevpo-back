package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * POST /api/portfolio/activities (create) and PUT /api/portfolio/activities/{id} (update).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRequest {

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private LocalDate start_date;

    private LocalDate end_date;

    /** Category: "activity", "project", "certificate", "camp", "other", etc. */
    private String category;
}
