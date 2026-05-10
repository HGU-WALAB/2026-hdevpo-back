package com.csee.swplus.mileage.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * The current user's role on a portfolio repo and their contribution percentage.
 * Example: {@code {"role": "Frontend Developer", "contribution_percent": 70}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyRoleDto {

    private String role;

    @Min(0)
    @Max(100)
    private Integer contribution_percent;

    @JsonCreator
    public static MyRoleDto of(
            @JsonProperty("role") String role,
            @JsonProperty("contribution_percent") Integer contribution_percent) {
        return MyRoleDto.builder().role(role).contribution_percent(contribution_percent).build();
    }
}
