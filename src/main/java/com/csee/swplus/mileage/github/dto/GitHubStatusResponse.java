package com.csee.swplus.mileage.github.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubStatusResponse {
    private boolean connected;
    private String githubUsername; // GitHub username/id to display
}
