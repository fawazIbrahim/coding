package com.example.exceptionhandling.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(
        int status,
        String path,
        Instant timestamp,
        List<String> errors
) {
    public static ApiError of(int status, String path, String... errors) {
        return new ApiError(status, path, Instant.now(), List.of(errors));
    }

    public static ApiError of(int status, String path, List<String> errors) {
        return new ApiError(status, path, Instant.now(), errors);
    }
}
