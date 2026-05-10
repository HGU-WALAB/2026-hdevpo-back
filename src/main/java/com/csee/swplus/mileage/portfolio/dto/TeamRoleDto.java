package com.csee.swplus.mileage.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * One role/headcount entry inside {@code team_composition} on a portfolio repo.
 * Example: {@code {"role": "FE", "count": 2}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamRoleDto {

    @NotBlank
    private String role;

    @NotNull
    @Min(0)
    private Integer count;

    @JsonCreator
    public static TeamRoleDto of(
            @JsonProperty("role") String role,
            @JsonProperty("count") Integer count) {
        return TeamRoleDto.builder().role(role).count(count).build();
    }
}
