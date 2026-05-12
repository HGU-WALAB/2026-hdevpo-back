package com.csee.swplus.mileage.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.Valid;

/**
 * Request body for {@code POST /api/portfolio/cv/{id}/generate-html}.
 * <p>
 * Used by the FE "AI Customize" step to: (1) rebuild the prompt with a new {@code design_preferences},
 * (2) call OpenAI, (3) save the rendered HTML — all in one round-trip. The server overwrites
 * {@code prompt}, {@code design_preferences}, and {@code html_content} on the CV.
 * <p>
 * Send {@code design_preferences = null} (or an all-blank object) to clear the design and regenerate
 * with the default blue style.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CvRegeneratePromptRequest {

    /**
     * New design preferences to render into STEP 2 ({@code [design_preferences]}). Pass {@code null}
     * (or all-blank) to clear and fall back to the default blue style.
     */
    @Valid
    private DesignPreferencesDto design_preferences;

    /**
     * Optional OpenAI model override (e.g. {@code "gpt-4o-mini"}). Falls back to {@code openai.api.model}
     * (default {@code gpt-4o}). Pass {@code null} to use the server default.
     */
    private String model;
}
