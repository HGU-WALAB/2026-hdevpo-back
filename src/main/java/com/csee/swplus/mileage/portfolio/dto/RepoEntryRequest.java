package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

/**
 * One item in PUT /api/portfolio/repositories body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RepoEntryRequest {

    @NotNull
    private Long repo_id;

    private String custom_title;

    /**
     * Optional user override stored on the link. Omit on PUT when unchanged; do not send GET’s merged
     * GitHub+user text unless you intend to persist it as the override (it would hide future GitHub updates).
     */
    private String description;

    private Boolean is_visible;
}
