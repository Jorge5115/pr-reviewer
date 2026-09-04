package com.jorge.prreviewer.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidDiffException.class)
    public ResponseEntity<Map<String, String>> handleInvalidDiff(InvalidDiffException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(LlmResponseException.class)
    public ResponseEntity<Map<String, String>> handleLlmResponse(LlmResponseException ex) {
        return ResponseEntity.status(502)
                .body(Map.of("error", ex.getMessage()));
    }
}
