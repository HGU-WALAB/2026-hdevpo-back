package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.Valid;

/**
 * Request body for PATCH /api/portfolio/cv/{id}.
 * Partial update: any field left {@code null} is unchanged.
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

    /** When set, toggles whether the CV HTML is reachable without login (public URL). */
    private Boolean is_public;

    /**
     * Editable raw prompt text (the assembled string the user copies into the LLM).
     * The server does NOT regenerate this when {@link #design_preferences} changes — user edits are kept as-is.
     * Send {@code null} to leave unchanged; send {@code ""} to clear.
     */
    private String prompt;

    /**
     * Editable design preferences (form values shown on the "customize prompt" screen).
     * Sub-fields all blank → stored as {@code null}. {@code null} = leave unchanged.
     */
    @Valid
    private DesignPreferencesDto design_preferences;
}
