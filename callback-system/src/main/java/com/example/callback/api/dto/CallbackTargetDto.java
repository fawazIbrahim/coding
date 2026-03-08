package com.example.callback.api.dto;

import com.example.callback.domain.AuthType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;
import java.util.UUID;

/**
 * DTO for creating and updating callback targets via the admin API.
 * {@code authConfig} is write-only — it is never returned in GET responses.
 */
public record CallbackTargetDto(
        UUID id,

        @NotBlank(message = "name must not be blank")
        String name,

        @NotBlank(message = "url must not be blank")
        String url,

        @Pattern(regexp = "GET|POST|PUT|PATCH", message = "httpMethod must be GET, POST, PUT, or PATCH")
        String httpMethod,

        Map<String, String> headers,

        @NotNull(message = "authType must not be null")
        AuthType authType,

        /** Sensitive — accepted on write, redacted on read */
        Map<String, String> authConfig,

        @Min(value = 0, message = "maxRetries must be >= 0")
        int maxRetries,

        @Min(value = 1, message = "retryBaseDelaySeconds must be >= 1")
        long retryBaseDelaySeconds,

        double retryBackoffMultiplier,

        @Min(value = 1, message = "timeoutSeconds must be >= 1")
        int timeoutSeconds,

        boolean enabled) {
}
