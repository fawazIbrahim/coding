package com.example.callback.service;

import com.example.callback.config.AppProperties;
import com.example.callback.domain.CallbackExecution;
import com.example.callback.domain.ExecutionStatus;
import com.example.callback.repository.CallbackExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Periodically resets executions that are ready to retry back to PENDING
 * so the {@code CallbackPoller} picks them up on its next sweep.
 *
 * <p>This service has a single, clear responsibility: move
 * {@code RETRY_SCHEDULED} → {@code PENDING} when the delay has elapsed.
 * The poller handles all actual dispatching.
 */
@Service
public class RetrySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(RetrySchedulerService.class);

    private final CallbackExecutionRepository executionRepository;
    private final AppProperties props;

    public RetrySchedulerService(CallbackExecutionRepository executionRepository,
                                 AppProperties props) {
        this.executionRepository = executionRepository;
        this.props = props;
    }

    /** Resets due retries to PENDING — the poller will claim them next cycle */
    @Scheduled(fixedDelayString = "${callback.retry.scheduler-interval-ms:30000}")
    @Transactional
    public void resetDueRetries() {
        List<CallbackExecution> due = executionRepository.findDueForRetry(Instant.now());
        if (due.isEmpty()) return;

        log.info("Resetting {} execution(s) from RETRY_SCHEDULED to PENDING", due.size());
        due.forEach(e -> {
            e.setStatus(ExecutionStatus.PENDING);
            e.setNextRetryAt(null);
        });
        executionRepository.saveAll(due);
    }

    /**
     * Rescues executions that got stuck in PENDING — this can happen if a poller
     * pod acquired the SKIP LOCKED row lock but crashed before committing the
     * IN_PROGRESS transition. PostgreSQL releases the lock on disconnect, but the
     * row stays PENDING. This job re-queues them after the configured threshold.
     */
    @Scheduled(fixedDelayString = "${callback.retry.scheduler-interval-ms:30000}",
               initialDelay = 60_000)
    @Transactional
    public void recoverStuckPending() {
        long thresholdMinutes = props.poller().stuckThresholdMinutes();
        Instant threshold = Instant.now().minusSeconds(thresholdMinutes * 60);

        List<CallbackExecution> stuck = executionRepository.findStuckPending(threshold);
        if (stuck.isEmpty()) return;

        log.warn("Found {} PENDING execution(s) older than {} min — re-queueing",
                stuck.size(), thresholdMinutes);
        // No status change needed: they are already PENDING, poller will claim them.
        // Touch updatedAt so we know the recovery ran.
        stuck.forEach(e -> executionRepository.save(e));
    }
}
