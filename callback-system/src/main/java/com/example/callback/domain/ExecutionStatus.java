package com.example.callback.domain;

public enum ExecutionStatus {
    /** Created, waiting to be published to Kafka */
    PENDING,
    /** Claimed by a consumer, execution in flight */
    IN_PROGRESS,
    /** Callback delivered successfully */
    SUCCESS,
    /** Failed, retry scheduled based on target policy */
    RETRY_SCHEDULED,
    /** All retry attempts exhausted without success */
    EXHAUSTED
}
