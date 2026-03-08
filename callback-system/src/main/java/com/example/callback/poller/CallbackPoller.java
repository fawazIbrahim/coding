package com.example.callback.poller;

import com.example.callback.config.AppProperties;
import com.example.callback.service.CallbackExecutorService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Polls PostgreSQL for PENDING executions using {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * and dispatches each one to a virtual-thread worker.
 *
 * <h2>Why SKIP LOCKED?</h2>
 * <ul>
 *   <li>Multiple pods can poll concurrently — each claims a non-overlapping batch.</li>
 *   <li>No distributed lock needed; the DB row lock is the coordination primitive.</li>
 *   <li>Claiming + status transition happen in one short transaction, so there is
 *       no window where an execution is lost if the app crashes after claiming.</li>
 * </ul>
 *
 * <h2>Concurrency model</h2>
 * The {@code @Scheduled} method runs on a single virtual thread (one at a time).
 * It claims a batch synchronously, then fans out HTTP calls to a
 * {@code newVirtualThreadPerTaskExecutor}, so all HTTP I/O is non-blocking.
 */
@Component
public class CallbackPoller {

    private static final Logger log = LoggerFactory.getLogger(CallbackPoller.class);

    private final CallbackExecutorService executorService;
    private final AppProperties props;
    // Unbounded virtual-thread pool — each task is a lightweight JDK thread
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public CallbackPoller(CallbackExecutorService executorService, AppProperties props) {
        this.executorService = executorService;
        this.props = props;
    }

    /**
     * Main poll loop. Fixed-delay ensures the next sweep starts only after the
     * previous one finishes claiming (not after HTTP calls complete).
     */
    @Scheduled(fixedDelayString = "${callback.poller.poll-interval-ms:1000}")
    public void poll() {
        int batchSize = props.poller().batchSize();
        List<UUID> claimed = executorService.claimNextBatch(batchSize);

        if (claimed.isEmpty()) return;

        log.debug("Claimed {} execution(s) for processing", claimed.size());
        for (UUID id : claimed) {
            virtualExecutor.submit(() -> {
                try {
                    executorService.execute(id);
                } catch (Exception e) {
                    log.error("Unhandled error processing execution={}: {}", id, e.getMessage(), e);
                }
            });
        }
    }

    /** Allow in-flight HTTP calls to complete before the JVM exits */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down callback virtual-thread executor...");
        virtualExecutor.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Virtual executor did not terminate cleanly within 30s");
                virtualExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            virtualExecutor.shutdownNow();
        }
    }
}
