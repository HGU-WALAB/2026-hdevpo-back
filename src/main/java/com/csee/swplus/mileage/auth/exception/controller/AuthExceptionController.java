package com.csee.swplus.mileage.auth.exception.controller;

import com.csee.swplus.mileage.auth.exception.DoNotExistException;
import com.csee.swplus.mileage.auth.exception.FailedHisnetLoginException;
import com.csee.swplus.mileage.base.response.ExceptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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