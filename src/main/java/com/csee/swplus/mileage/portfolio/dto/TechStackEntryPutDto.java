package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One technology in PUT body.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TechStackEntryPutDto {

    private String name;
    /** Proficiency 1–100. */
    private Integer level;
}
