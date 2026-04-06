package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * PATCH /api/portfolio/repositories/github/batch — same field semantics as {@link RepoPatchRequest}
 * applied to each GitHub {@code repo_id}; creates portfolio links for ids that are not yet selected.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepoGithubBatchPatchRequest {

    @NotEmpty
    @Size(max = 100)
    private List<Long> repo_ids;

    /** Applied to every repo (only non-null fields). Omit or null for defaults / no changes. */
    @Valid
    private RepoPatchRequest patch;
}
