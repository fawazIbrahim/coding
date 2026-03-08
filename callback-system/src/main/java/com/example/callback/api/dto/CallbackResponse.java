package com.example.callback.api.dto;

import com.example.callback.domain.ExecutionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Execution status response returned by the callback API.
 */
public record CallbackResponse(
        UUID executionId,
        String callbackType,
        String target,
        ExecutionStatus status,
        int attemptCount,
        int maxAttempts,
        Instant nextRetryAt,
        Instant createdAt,
        Instant updatedAt) {
}
