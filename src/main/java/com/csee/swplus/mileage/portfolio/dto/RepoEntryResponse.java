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
     * Display text: non-blank value from PATCH on the portfolio link, otherwise
     * GitHub repo description
     * (from cache / last enrich). PATCH with {@code ""} clears the override and
     * shows GitHub again.
     */
    private String description;
    /** GitHub repository short description from cache / API (may be null). */
    private String github_description;
    private Boolean is_visible;
    private Integer display_order;

    /**
     * GitHub repository {@code name} field (repo slug), same source as
     * {@link #github_description}.
     */
    private String github_title;
    private String html_url;
    /** Primary language (from repo list API). Kept for backward compatibility. */
    private String language;
    /**
     * All languages from GET /repos/{owner}/{repo}/languages, sorted by byte count
     * desc, with percentage.
     */
    private List<RepoLanguageDto> languages;
    private String created_at;
    private String updated_at;
    /** public or private */
    private String visibility;
    /** Owner login (user or org) */
    private String owner;
    /**
     * Reserved; no longer populated (removed extra GitHub /commits calls per repo).
     * Always null.
     */
    private Integer commit_count;
    /** Star count (from GitHub API). Null if unavailable. */
    private Integer stargazers_count;
    /** Fork count (from GitHub API). Null if unavailable. */
    private Integer forks_count;

    /**
     * Team composition for this repo (role + headcount per role). May be
     * {@code null} or empty.
     */
    private List<TeamRoleDto> team_composition;
    /**
     * Current user's role and contribution percentage on this repo. {@code null}
     * when not set.
     */
    private MyRoleDto my_role;
    /**
     * Free-text description of the user's key contributions on this repo
     * (multi-line).
     */
    private String key_contributions;
}
