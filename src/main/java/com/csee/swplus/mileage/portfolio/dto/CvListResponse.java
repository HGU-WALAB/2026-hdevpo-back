package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for GET /api/portfolio/cv (list of CVs).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvListResponse {

    private List<CvListItem> cvs;

    /** Total non-deleted CVs for the user (same scope as {@code cvs} before pagination). */
    private Integer total;
}
