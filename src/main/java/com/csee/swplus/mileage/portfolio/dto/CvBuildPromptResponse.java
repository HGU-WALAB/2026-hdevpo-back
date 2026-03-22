package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response for POST /api/portfolio/cv/build-prompt.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvBuildPromptResponse {

    /** Full prompt ready to paste into LLM. */
    private String prompt;
}
