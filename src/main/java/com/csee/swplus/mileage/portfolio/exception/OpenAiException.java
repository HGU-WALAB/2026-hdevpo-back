package com.csee.swplus.mileage.portfolio.exception;

/**
 * Base for OpenAI API failures during CV HTML generation. Subclasses map to specific HTTP statuses
 * in {@link com.csee.swplus.mileage.auth.exception.controller.AuthExceptionController}.
 */
public class OpenAiException extends RuntimeException {
    public OpenAiException(String message) {
        super(message);
    }
    public OpenAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
