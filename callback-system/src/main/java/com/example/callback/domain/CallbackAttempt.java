package com.example.callback.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a single HTTP delivery attempt.
 * Written once and never updated — full audit trail of every try.
 */
@Entity
@Table(name = "callback_attempts",
        indexes = @Index(name = "idx_attempts_execution_id", columnList = "execution_id"))
public class CallbackAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private CallbackExecution execution;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    /** Truncated to 4000 chars to avoid bloating the table */
    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    // ── Getters & Setters ───────────────────────────────────────────────────

    public UUID getId() { return id; }

    public CallbackExecution getExecution() { return execution; }
    public void setExecution(CallbackExecution execution) { this.execution = execution; }

    public int getAttemptNumber() { return attemptNumber; }
    public void setAttemptNumber(int attemptNumber) { this.attemptNumber = attemptNumber; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public Integer getResponseStatusCode() { return responseStatusCode; }
    public void setResponseStatusCode(Integer responseStatusCode) { this.responseStatusCode = responseStatusCode; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
}
