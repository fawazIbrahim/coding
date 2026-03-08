package com.example.callback.service;

import com.example.callback.domain.CallbackAttempt;
import com.example.callback.domain.CallbackExecution;
import com.example.callback.domain.CallbackTarget;
import com.example.callback.domain.ExecutionStatus;
import com.example.callback.repository.CallbackAttemptRepository;
import com.example.callback.repository.CallbackExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Handles the two core operations of the callback engine:
 *
 * <ol>
 *   <li>{@link #claimNextBatch} — runs a {@code SKIP LOCKED} query inside a
 *       short transaction, transitions claimed rows to {@code IN_PROGRESS}, and
 *       returns their IDs for the poller to dispatch.</li>
 *   <li>{@link #execute} — loads a single {@code IN_PROGRESS} execution, fires
 *       the HTTP call, persists the attempt record, and transitions the execution
 *       to {@code SUCCESS}, {@code RETRY_SCHEDULED}, or {@code EXHAUSTED}.</li>
 * </ol>
 */
@Service
public class CallbackExecutorService {

    private static final Logger log = LoggerFactory.getLogger(CallbackExecutorService.class);
    private static final int MAX_RESPONSE_BODY_LENGTH = 4000;

    private final CallbackExecutionRepository executionRepository;
    private final CallbackAttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public CallbackExecutorService(CallbackExecutionRepository executionRepository,
                                   CallbackAttemptRepository attemptRepository,
                                   ObjectMapper objectMapper) {
        this.executionRepository = executionRepository;
        this.attemptRepository = attemptRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Claims the next batch of PENDING executions using SKIP LOCKED and marks
     * them IN_PROGRESS — all within a single short-lived DB transaction.
     * Returns the IDs so the poller can fan out to virtual threads.
     */
    @Transactional
    public List<UUID> claimNextBatch(int batchSize) {
        List<CallbackExecution> batch = executionRepository.findPendingWithLock(batchSize);
        if (batch.isEmpty()) return List.of();

        batch.forEach(e -> e.setStatus(ExecutionStatus.IN_PROGRESS));
        executionRepository.saveAll(batch);   // dirty-check flushes the status update

        return batch.stream().map(CallbackExecution::getId).toList();
    }

    /**
     * Executes the HTTP callback for one claimed execution.
     * Each call runs in its own transaction so failures are isolated.
     */
    @Transactional
    public void execute(UUID executionId) {
        CallbackExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalStateException("Execution not found: " + executionId));
        CallbackTarget target = execution.getTarget(); // eager or loaded within tx

        int attemptNumber = execution.getAttemptCount() + 1;
        Instant attemptStart = Instant.now();

        CallbackAttempt attempt = new CallbackAttempt();
        attempt.setExecution(execution);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setAttemptedAt(attemptStart);

        try {
            HttpResponse<String> response = fireHttp(execution, target, attemptNumber);
            long duration = Duration.between(attemptStart, Instant.now()).toMillis();
            boolean ok = isSuccess(response.statusCode());

            attempt.setSuccess(ok);
            attempt.setResponseStatusCode(response.statusCode());
            attempt.setResponseBody(truncate(response.body()));
            attempt.setDurationMs(duration);

            if (ok) {
                execution.setStatus(ExecutionStatus.SUCCESS);
                execution.setAttemptCount(attemptNumber);
                log.info("SUCCESS execution={} attempt={} httpStatus={}",
                        executionId, attemptNumber, response.statusCode());
            } else {
                log.warn("FAILED (non-2xx) execution={} attempt={} httpStatus={}",
                        executionId, attemptNumber, response.statusCode());
                recordFailure(execution, target, attemptNumber);
            }
        } catch (Exception e) {
            attempt.setSuccess(false);
            attempt.setErrorMessage(e.getMessage());
            attempt.setDurationMs(Duration.between(attemptStart, Instant.now()).toMillis());
            log.error("ERROR execution={} attempt={}: {}", executionId, attemptNumber, e.getMessage());
            recordFailure(execution, target, attemptNumber);
        }

        attemptRepository.save(attempt);
        executionRepository.save(execution);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private HttpResponse<String> fireHttp(CallbackExecution execution,
                                          CallbackTarget target,
                                          int attemptNumber) throws Exception {
        String body = objectMapper.writeValueAsString(
                new CallbackPayload(execution.getCallbackType(), execution.getData(),
                        execution.getId().toString()));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(target.getUrl()))
                .timeout(Duration.ofSeconds(target.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("X-Callback-Type", execution.getCallbackType())
                .header("X-Execution-Id", execution.getId().toString())
                .header("X-Attempt-Number", String.valueOf(attemptNumber));

        if (target.getHeaders() != null) {
            target.getHeaders().forEach(builder::header);
        }
        applyAuth(builder, target);

        HttpRequest request = switch (target.getHttpMethod().toUpperCase()) {
            case "GET"   -> builder.GET().build();
            case "PUT"   -> builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
            default      -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        };

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void applyAuth(HttpRequest.Builder builder, CallbackTarget target) {
        if (target.getAuthConfig() == null) return;
        switch (target.getAuthType()) {
            case BEARER_TOKEN ->
                    builder.header("Authorization", "Bearer " + target.getAuthConfig().get("token"));
            case BASIC_AUTH -> {
                String creds = target.getAuthConfig().get("username") + ":"
                        + target.getAuthConfig().get("password");
                builder.header("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(creds.getBytes()));
            }
            case API_KEY ->
                    builder.header(target.getAuthConfig().getOrDefault("header", "X-Api-Key"),
                            target.getAuthConfig().get("key"));
            case NONE -> { /* no-op */ }
        }
    }

    private void recordFailure(CallbackExecution execution, CallbackTarget target, int attemptNumber) {
        execution.setAttemptCount(attemptNumber);
        if (attemptNumber < execution.getMaxAttempts()) {
            long delaySecs = computeDelay(target, attemptNumber);
            execution.setStatus(ExecutionStatus.RETRY_SCHEDULED);
            execution.setNextRetryAt(Instant.now().plusSeconds(delaySecs));
            log.info("Retry scheduled execution={} in {}s (attempt {}/{})",
                    execution.getId(), delaySecs, attemptNumber, execution.getMaxAttempts());
        } else {
            execution.setStatus(ExecutionStatus.EXHAUSTED);
            log.warn("All retries exhausted execution={}", execution.getId());
        }
    }

    /** Exponential backoff: baseDelay × multiplier^(attempt-1) */
    private long computeDelay(CallbackTarget target, int attemptNumber) {
        return Math.round(target.getRetryBaseDelaySeconds()
                * Math.pow(target.getRetryBackoffMultiplier(), attemptNumber - 1));
    }

    private boolean isSuccess(int code) { return code >= 200 && code < 300; }

    private String truncate(String s) {
        if (s == null || s.length() <= MAX_RESPONSE_BODY_LENGTH) return s;
        return s.substring(0, MAX_RESPONSE_BODY_LENGTH) + "…";
    }

    private record CallbackPayload(String type, Object data, String executionId) {}
}
