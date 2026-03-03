package com.example.springintegration.exception;

/**
 * Thrown when the remote microservice responds with an HTTP error status that
 * should NOT be retried (e.g., 4xx errors other than 429).
 *
 * <p>This exception causes the retry advice to skip all retry attempts and
 * immediately delegate to the error handler.</p>
 */
public class NonRetryableServiceException extends RuntimeException {

    private final int statusCode;

    public NonRetryableServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public NonRetryableServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return "NonRetryableServiceException{statusCode=" + statusCode + ", message='" + getMessage() + "'}";
    }
}
