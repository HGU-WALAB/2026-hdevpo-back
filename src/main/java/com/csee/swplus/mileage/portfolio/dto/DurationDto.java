package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Repo duration on GET: GitHub timestamps from cache plus optional user overrides on the portfolio link.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DurationDto {

    /** From GitHub cache ({@code github_created_at}). */
    private String started_at_github;
    /** From GitHub cache ({@code github_updated_at}). */
    private String updated_at_github;
    /** User override (ISO-8601). {@code null} when not set. */
    private String started_at;
    /** User override (ISO-8601). {@code null} when not set. */
    private String updated_at;
}
