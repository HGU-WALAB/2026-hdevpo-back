package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Result of POST …/repositories/github-cache/refresh */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubRepoCacheSyncResult {
    @Builder.Default
    private int reposSynced = 0;

    /**
     * Machine-readable prefixes before {@code ": "} (e.g. {@code NO_GITHUB_TOKEN}, {@code GITHUB_SCOPE}).
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
