package com.example.callback.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the lifecycle of a single callback request from submission to completion.
 * Uses optimistic locking (@Version) to prevent concurrent status corruption.
 */
@Entity
@Table(name = "callback_executions", indexes = {
        @Index(name = "idx_executions_status", columnList = "status"),
        @Index(name = "idx_executions_target_id", columnList = "target_id"),
        @Index(name = "idx_executions_created_at", columnList = "created_at")
})
public class CallbackExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Callback type as provided by the caller (e.g. "ORDER_SHIPPED") */
    @Column(name = "callback_type", nullable = false)
    private String callbackType;

    /** Arbitrary JSON payload from the caller */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> data;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id", nullable = false)
    private CallbackTarget target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExecutionStatus status = ExecutionStatus.PENDING;

    /** Number of attempts already made */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    /** Total allowed attempts (retries + 1), copied from target at creation time */
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    /** Wall-clock time after which this execution is eligible for retry */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CallbackAttempt> attempts = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock version — prevents concurrent modification */
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ── Getters & Setters ───────────────────────────────────────────────────

    public UUID getId() { return id; }

    public String getCallbackType() { return callbackType; }
    public void setCallbackType(String callbackType) { this.callbackType = callbackType; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public CallbackTarget getTarget() { return target; }
    public void setTarget(CallbackTarget target) { this.target = target; }

    public ExecutionStatus getStatus() { return status; }
    public void setStatus(ExecutionStatus status) { this.status = status; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public List<CallbackAttempt> getAttempts() { return attempts; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
