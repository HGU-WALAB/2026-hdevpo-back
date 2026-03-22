package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for PATCH /api/portfolio/cv/{id}.
 * Only title and html_content are editable.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CvPatchRequest {

    /** Editable title. */
    private String title;

    /** Editable HTML content. */
    private String html_content;
}
