package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Domain with nested tech stacks (GET response).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechStackDomainResponse {

    private Integer id;
    private String name;
    private Integer order_index;
    private List<TechStackEntryResponse> tech_stacks;
}
