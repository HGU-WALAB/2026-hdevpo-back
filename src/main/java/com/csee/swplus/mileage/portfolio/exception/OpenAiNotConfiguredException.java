package com.csee.swplus.mileage.portfolio.exception;

/**
 * Thrown when {@code openai.api.key} is missing/blank — endpoint maps this to HTTP 503
 * so the FE can show "AI 설정이 누락되었습니다" rather than a generic 500.
 */
public class OpenAiNotConfiguredException extends OpenAiException {
    public OpenAiNotConfiguredException(String message) {
        super(message);
    }
}
