package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PATCH body fragment for duration overrides only.
 * <ul>
 *   <li>{@code null} for a field = leave that override unchanged.</li>
 *   <li>{@code ""} = clear that override (fall back to GitHub value on display).</li>
 *   <li>Non-empty string = replace override (must be ISO-8601).</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DurationPatchDto {

    private String started_at;
    private String updated_at;
}
