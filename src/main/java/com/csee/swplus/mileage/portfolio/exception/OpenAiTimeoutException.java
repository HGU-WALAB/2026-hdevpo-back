package com.csee.swplus.mileage.portfolio.exception;

/**
 * Thrown when the OpenAI request exceeds the configured timeout — maps to HTTP 504.
 * The CV's prompt is still saved (intent preserved), so the FE can offer a "재시도" button.
 */
public class OpenAiTimeoutException extends OpenAiException {
    public OpenAiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
