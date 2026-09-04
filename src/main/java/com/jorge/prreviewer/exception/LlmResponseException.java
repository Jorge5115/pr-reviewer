package com.jorge.prreviewer.exception;

public class LlmResponseException extends RuntimeException {

    public LlmResponseException(String message) {
        super(message);
    }

    public LlmResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
