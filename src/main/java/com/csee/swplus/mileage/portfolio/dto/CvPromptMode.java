package com.csee.swplus.mileage.portfolio.dto;

/**
 * {@code POST /api/portfolio/cv/build-prompt} prompt style. Stored on {@code PortfolioCv.mode}.
 */
public enum CvPromptMode {

    CV("cv"),
    ARCHIVE("archive");

    private final String value;

    CvPromptMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * {@code null} or blank → {@link #CV}. Otherwise {@code cv} / {@code archive} (case-insensitive).
     *
     * @throws IllegalArgumentException if non-blank and not a known mode
     */
    public static CvPromptMode fromRequest(String mode) {
        if (mode == null) {
            return CV;
        }
        String s = mode.trim();
        if (s.isEmpty()) {
            return CV;
        }
        if ("cv".equalsIgnoreCase(s)) {
            return CV;
        }
        if ("archive".equalsIgnoreCase(s)) {
            return ARCHIVE;
        }
        throw new IllegalArgumentException("mode must be \"cv\" or \"archive\"");
    }
}
