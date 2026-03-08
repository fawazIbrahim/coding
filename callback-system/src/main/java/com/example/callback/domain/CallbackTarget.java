package com.example.callback.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent configuration for a callback destination.
 * Targets are managed via the admin API or seeded in the database.
 */
@Entity
@Table(name = "callback_targets",
        indexes = @Index(name = "uq_callback_targets_name", columnList = "name", unique = true))
public class CallbackTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique logical name used by callers to reference this target */
    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod = "POST";

    /** Optional static headers sent with every request */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 50)
    private AuthType authType = AuthType.NONE;

    /**
     * Auth credentials — stored encrypted at rest by the DB operator.
     * Keys: "token" (BEARER_TOKEN), "username"/"password" (BASIC_AUTH),
     *       "header"/"key" (API_KEY).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "auth_config", columnDefinition = "jsonb")
    private Map<String, String> authConfig;

    /** Number of retries after initial failure (0 = no retries) */
    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 3;

    /** Base delay between retry attempts (seconds) */
    @Column(name = "retry_base_delay_seconds", nullable = false)
    private long retryBaseDelaySeconds = 60L;

    /** Exponential backoff multiplier applied per attempt */
    @Column(name = "retry_backoff_multiplier", nullable = false)
    private double retryBackoffMultiplier = 2.0;

    /** Per-request HTTP timeout (seconds) */
    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 30;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public AuthType getAuthType() { return authType; }
    public void setAuthType(AuthType authType) { this.authType = authType; }

    public Map<String, String> getAuthConfig() { return authConfig; }
    public void setAuthConfig(Map<String, String> authConfig) { this.authConfig = authConfig; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getRetryBaseDelaySeconds() { return retryBaseDelaySeconds; }
    public void setRetryBaseDelaySeconds(long retryBaseDelaySeconds) { this.retryBaseDelaySeconds = retryBaseDelaySeconds; }

    public double getRetryBackoffMultiplier() { return retryBackoffMultiplier; }
    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) { this.retryBackoffMultiplier = retryBackoffMultiplier; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
