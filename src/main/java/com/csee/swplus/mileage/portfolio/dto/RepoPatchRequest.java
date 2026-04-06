package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PATCH /api/portfolio/repositories/{id} – partial update for a single repo entry.
 * Only non-null fields are applied. For {@code description}, send an empty string to clear the user
 * override and fall back to GitHub text on GET.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RepoPatchRequest {

    private String custom_title;
    private String description;
    private Boolean is_visible;
    private Integer display_order;
}

