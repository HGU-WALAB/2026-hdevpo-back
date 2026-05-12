package com.csee.swplus.mileage.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;

/**
 * User-supplied design preferences for the CV prompt (Step 2).
 * Korean labels are sent verbatim by the FE; the BE renders them under {@code [design_preferences]}.
 *
 * <pre>
 * [design_preferences]
 * - 레이아웃: 랜딩 페이지
 * - 색상 테마: 인디고
 * - 밀도: 페이지 제한 없음
 * - 추가 요청사항: ...
 * </pre>
 *
 * Each field is optional; blank fields are omitted from the rendered block.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignPreferencesDto {

    @Size(max = 50)
    private String layout;

    @Size(max = 50)
    private String color_theme;

    @Size(max = 50)
    private String density;

    @Size(max = 1000)
    private String additional_notes;

    @JsonCreator
    public static DesignPreferencesDto of(
            @JsonProperty("layout") String layout,
            @JsonProperty("color_theme") String color_theme,
            @JsonProperty("density") String density,
            @JsonProperty("additional_notes") String additional_notes) {
        return DesignPreferencesDto.builder()
                .layout(layout)
                .color_theme(color_theme)
                .density(density)
                .additional_notes(additional_notes)
                .build();
    }
}
