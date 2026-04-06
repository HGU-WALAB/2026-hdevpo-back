package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One item in GET /api/portfolio/repositories response.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoEntryResponse {

    private Long id;
    private Long repo_id;
    private String custom_title;
    /**
     * Display text: non-blank value from PATCH on the portfolio link, otherwise GitHub repo description
     * (from cache / last enrich). PATCH with {@code ""} clears the override and shows GitHub again.
     */
    private String description;
    private Boolean is_visible;
    private Integer display_order;

    // Live GitHub repo data (public API)
    private String name;
    private String html_url;
    /** Primary language (from repo list API). Kept for backward compatibility. */
    private String language;
    /** All languages from GET /repos/{owner}/{repo}/languages, sorted by byte count desc, with percentage. */
    private List<RepoLanguageDto> languages;
    private String created_at;
    private String updated_at;
    /** public or private */
    private String visibility;
    /** Owner login (user or org) */
    private String owner;
    /** Reserved; no longer populated (removed extra GitHub /commits calls per repo). Always null. */
    private Integer commit_count;
    /** Star count (from GitHub API). Null if unavailable. */
    private Integer stargazers_count;
    /** Fork count (from GitHub API). Null if unavailable. */
    private Integer forks_count;
}
