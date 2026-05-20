package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

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

    @Size(max = 255)
    private String host;

    @Size(max = 500)
    private String role;

    @Size(max = 2000)
    private String achievements;

    @Size(max = 5000)
    private String achievements_detail;

    @NotNull
    private LocalDate start_date;

    private LocalDate end_date;

    /** Category: "activity", "project", "certificate", "camp", "other", etc. */
    private String category;

    /** Optional external link. */
    @Size(max = 2048)
    private String url;

    /** Optional tags (e.g. for future filter/sort). */
    private List<String> tags;
}
