package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Domain + tech stacks in PUT body (full replace).
 * {@code tech_stacks} may be null, omitted, or empty — domain row is still saved with no tech entries.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TechStackDomainPutDto {

    private String name;
    private Integer order_index;
    private List<TechStackEntryPutDto> tech_stacks;
}
