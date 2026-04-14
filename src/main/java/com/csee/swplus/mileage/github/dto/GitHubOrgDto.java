package com.csee.swplus.mileage.github.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubOrgDto {
    private Long id;
    private String owner;
    private String avatarUrl;
}

