package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * One item in PATCH /api/portfolio/activities (full list).
 * id is required; other fields are optional (only non-null are applied).
 * Category: "activity", "project", "certificate", "camp", "other", etc.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPatchItemRequest {

    @javax.validation.constraints.NotNull
    private Long id;

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

    private LocalDate start_date;
    private LocalDate end_date;
    private String category;

    private String url;
    private List<String> tags;
}
