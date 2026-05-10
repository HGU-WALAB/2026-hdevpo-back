package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * PATCH /api/portfolio/repositories/{id} – partial update for a single repo
 * entry.
 * Only non-null fields are applied. For {@code description}, send an empty
 * string to clear the user
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

    /**
     * Full team composition for this repo. Each entry is {role, count}.
     * Pass {@code null} to keep current value, {@code []} to clear.
     */
    @Valid
    private List<TeamRoleDto> team_composition;

    /**
     * Current user's role and contribution percentage (0–100). Pass {@code null} to
     * keep current.
     */
    @Valid
    private MyRoleDto my_role;

    /**
     * Free-text key contributions (multi-line). Used by the FE as a
     * placeholder/checklist of
     * what the user did on this repo (e.g. troubleshooting, integrations, results).
     */
    @Size(max = 2000)
    private String key_contributions;

    /**
     * Optional duration overrides ({@code started_at}, {@code updated_at} as ISO-8601).
     * {@code null} = do not change duration overrides.
     */
    @Valid
    private DurationPatchDto duration;
}
