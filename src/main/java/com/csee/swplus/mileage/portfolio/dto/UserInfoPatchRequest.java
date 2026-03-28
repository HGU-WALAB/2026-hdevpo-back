package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;
import java.util.List;

/**
 * PATCH /api/portfolio/user-info (application/json) — bio, profile_image_url, profile_links.
 * 이미지 파일 업로드는 PUT /api/portfolio/user-info/image.
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
