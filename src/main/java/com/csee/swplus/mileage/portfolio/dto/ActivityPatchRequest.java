package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * PATCH /api/portfolio/activities/{id} – partial update (only non-null fields are applied).
 * Category: "activity", "project", "certificate", "camp", "other", etc.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPatchRequest {

    private String title;
    private String description;

    /** Null = unchanged; empty string clears. */
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

    /** Null = leave unchanged; empty string clears. */
    private String url;

    /** Null = leave unchanged; empty list clears tags. */
    private List<String> tags;
}
