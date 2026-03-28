package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Optional labeled URL on user profile (e.g. blog, LinkedIn).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileLinkDto {

    /** Display label (e.g. "Blog", "LinkedIn"). */
    private String label;

    /** Full URL (http/https). */
    private String url;
}
