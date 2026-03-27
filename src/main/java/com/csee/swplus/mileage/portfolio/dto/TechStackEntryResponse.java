package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One technology under a domain (GET response).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechStackEntryResponse {

    private String name;
    /** Proficiency 1–100. */
    private Integer level;
}
