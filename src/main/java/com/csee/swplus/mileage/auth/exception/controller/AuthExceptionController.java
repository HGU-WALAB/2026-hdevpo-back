package com.csee.swplus.mileage.auth.exception.controller;

import com.csee.swplus.mileage.auth.exception.DoNotExistException;
import com.csee.swplus.mileage.auth.exception.FailedHisnetLoginException;
import com.csee.swplus.mileage.base.response.ExceptionResponse;
import com.csee.swplus.mileage.portfolio.exception.OpenAiException;
import com.csee.swplus.mileage.portfolio.exception.OpenAiNotConfiguredException;
import com.csee.swplus.mileage.portfolio.exception.OpenAiTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class AuthExceptionController {

    @ExceptionHandler(FailedHisnetLoginException.class)
    public ResponseEntity<ExceptionResponse> handleFailedHisnetLoginException(FailedHisnetLoginException e) {
        ExceptionResponse response = ExceptionResponse.builder()
                .error(e.getStatus().toString())
                .message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(DoNotExistException.class)
    public ResponseEntity<ExceptionResponse> handleDoNotExistException(DoNotExistException e) {
        log.warn("DoNotExistException: {}", e.getMessage());
        ExceptionResponse response = ExceptionResponse.builder()
                .error("Not Found")
                .message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ExceptionResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fieldName(fe) + ": " + (fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .collect(Collectors.joining("; "));
        if (message.isEmpty()) {
            message = "Validation failed";
        }
        log.warn("Validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ExceptionResponse.builder().error("Bad Request").message(message).build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ExceptionResponse> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("ConstraintViolationException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ExceptionResponse.builder().error("Bad Request").message(e.getMessage()).build());
    }

    private static String fieldName(FieldError fe) {
        return fe.getField() != null && !fe.getField().isEmpty() ? fe.getField() : fe.getObjectName();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());
        ExceptionResponse response = ExceptionResponse.builder()
                .error("Bad Request")
                .message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ExceptionResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("DataIntegrityViolationException: {}", e.getMessage());
        String msg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : e.getMessage();
        if (msg != null && msg.toLowerCase().contains("foreign key")) {
            msg = "존재하지 않는 mileage_id가 포함되어 있습니다. mileage_id가 _sw_mileage_record에 있는지 확인하세요.";
        } else if (msg != null && (msg.toLowerCase().contains("duplicate") || msg.toLowerCase().contains("unique"))) {
            msg = "중복된 mileage_id가 요청에 포함되어 있습니다.";
        }
        ExceptionResponse response = ExceptionResponse.builder()
                .error("Bad Request")
                .message(msg != null ? msg : "데이터 제약 조건 위반")
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /** OPENAI_API_KEY missing → 503 (FE: "AI 설정이 누락되었습니다, 잠시 후 다시 시도해 주세요"). */
    @ExceptionHandler(OpenAiNotConfiguredException.class)
    public ResponseEntity<ExceptionResponse> handleOpenAiNotConfigured(OpenAiNotConfiguredException e) {
        log.warn("OpenAI not configured: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ExceptionResponse.builder().error("Service Unavailable").message(e.getMessage()).build());
    }

    /** OpenAI request timed out → 504 (FE: "AI 응답이 지연되어 다시 시도해 주세요"). Prompt is still saved on the CV. */
    @ExceptionHandler(OpenAiTimeoutException.class)
    public ResponseEntity<ExceptionResponse> handleOpenAiTimeout(OpenAiTimeoutException e) {
        log.warn("OpenAI timeout: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(
                ExceptionResponse.builder().error("Gateway Timeout").message(e.getMessage()).build());
    }

    /** Generic OpenAI failure (HTTP error from OpenAI, parse failure, etc.) → 502. Prompt is still saved on the CV. */
    @ExceptionHandler(OpenAiException.class)
    public ResponseEntity<ExceptionResponse> handleOpenAiException(OpenAiException e) {
        log.warn("OpenAI error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                ExceptionResponse.builder().error("Bad Gateway").message(e.getMessage()).build());
    }

    /** Returns 500 with exception details in body so we can see the cause (e.g. for portfolio 500). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        ExceptionResponse response = ExceptionResponse.builder()
                .error("Internal Server Error")
                .message(e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : ""))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}