package com.example.springintegration.exception;

/**
 * Thrown when the remote microservice responds with an HTTP status that
 * warrants a retry: 5xx server errors or 429 Too Many Requests.
 *
 * <p>The retry advice is configured to re-attempt the call up to 3 times
 * when this exception is raised, before delegating to the error handler.</p>
 */
public class RetryableServiceException extends RuntimeException {

    private final int statusCode;

    public RetryableServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public RetryableServiceException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return "RetryableServiceException{statusCode=" + statusCode + ", message='" + getMessage() + "'}";
    }
}
