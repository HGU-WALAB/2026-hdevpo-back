package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * PATCH /api/portfolio/user-info – 소개글(bio) 및 프로필 이미지 수정 요청.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoPatchRequest {

    @Size(max = 5000)
    private String bio;

    private String profile_image_url;

    /**
     * Optional profile links. {@code null} = leave unchanged; {@code []} = clear all.
     */
    private List<ProfileLinkDto> profile_links;
}
