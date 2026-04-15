package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /api/portfolio/repositories – 레포지토리 목록 응답.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoriesResponse {

    private List<RepoEntryResponse> repositories;

    // NOTE: This response is used by GET and PATCH flows. Write-operation metadata
    // (warnings/skipped) is returned by dedicated DTOs (e.g. PUT response).
}
