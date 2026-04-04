package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Result of POST …/repositories/github-cache/refresh */
@Getter
@AllArgsConstructor
public class GithubRepoCacheSyncResult {
    private final int reposSynced;
}
