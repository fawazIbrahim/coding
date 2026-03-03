package com.example.springintegration.domain;

/**
 * Unified response object returned by the integration gateway.
 * Represents either a successful result or a handled failure.
 */
public class ProcessingResult {

    private final boolean success;
    private final Object data;
    private final String errorMessage;

    private ProcessingResult(boolean success, Object data, String errorMessage) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    public static ProcessingResult success(Object data) {
        return new ProcessingResult(true, data, null);
    }

    public static ProcessingResult failure(String errorMessage) {
        return new ProcessingResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return success
            ? "ProcessingResult{success=true, data=" + data + "}"
            : "ProcessingResult{success=false, error='" + errorMessage + "'}";
    }
}
