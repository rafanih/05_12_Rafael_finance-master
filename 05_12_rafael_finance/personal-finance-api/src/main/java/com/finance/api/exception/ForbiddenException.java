package com.finance.api.exception;

/**
 * Thrown when a user attempts to access a resource they do not own.
 * Maps to HTTP 403 Forbidden via GlobalExceptionHandler.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
