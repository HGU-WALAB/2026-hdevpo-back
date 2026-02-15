package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;

/**
 * PATCH /api/portfolio/user-info – 소개글(bio) 수정 요청.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoPatchRequest {

    @Size(max = 5000)
    private String bio;
}
