package com.example.callback.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for all {@code callback.*} properties.
 * Nested records map directly to YAML sub-keys.
 */
@ConfigurationProperties(prefix = "callback")
public record AppProperties(
        PollerProperties poller,
        RetryProperties retry,
        ExecutorProperties executor) {

    /** Controls how the DB poller claims and dispatches executions */
    public record PollerProperties(
            /** Max executions claimed per polling cycle */
            int batchSize,
            /** Delay between poll cycles in milliseconds */
            long pollIntervalMs,
            /** PENDING executions older than this (minutes) are treated as stuck */
            long stuckThresholdMinutes) {
    }

    public record RetryProperties(
            /** How often the retry-reset scheduler runs in milliseconds */
            long schedulerIntervalMs) {
    }

    public record ExecutorProperties(
            /** Fallback HTTP timeout when target has no explicit value configured */
            int defaultTimeoutSeconds) {
    }
}
