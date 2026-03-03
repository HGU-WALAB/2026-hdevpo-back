package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private String description;
    private Boolean is_visible;
    private Integer display_order;

    // Live GitHub repo data (public API)
    private String name;
    private String html_url;
    private String language;
    private String created_at;
    private String updated_at;
    /** public or private */
    private String visibility;
    /** Owner login (user or org) */
    private String owner;
}
