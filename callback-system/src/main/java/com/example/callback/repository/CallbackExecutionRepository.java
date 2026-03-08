package com.example.callback.repository;

import com.example.callback.domain.CallbackExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CallbackExecutionRepository extends JpaRepository<CallbackExecution, UUID> {

    /**
     * Claims up to {@code size} PENDING executions atomically.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} means:
     * <ul>
     *   <li>Rows already locked by another transaction (another pod's poll cycle)
     *       are silently skipped — no waiting, no deadlocks.</li>
     *   <li>The caller must update {@code status} to {@code IN_PROGRESS} and commit
     *       within the same transaction to release the lock with the new state.</li>
     * </ul>
     */
    @Query(value = """
            SELECT * FROM callback_executions
             WHERE status = 'PENDING'
             ORDER BY created_at ASC
             LIMIT :size
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CallbackExecution> findPendingWithLock(@Param("size") int size);

    /**
     * RETRY_SCHEDULED executions whose delay has elapsed.
     * The retry scheduler resets these to PENDING so the poller picks them up.
     */
    @Query("""
            SELECT e FROM CallbackExecution e
             WHERE e.status = 'RETRY_SCHEDULED'
               AND e.nextRetryAt <= :now
            """)
    List<CallbackExecution> findDueForRetry(@Param("now") Instant now);

    /**
     * PENDING executions older than the stuck threshold.
     * Guards against app-crash-during-claim edge cases where a row was locked
     * by a poller that crashed before committing the IN_PROGRESS transition.
     */
    @Query("""
            SELECT e FROM CallbackExecution e
             WHERE e.status = 'PENDING'
               AND e.createdAt <= :threshold
            """)
    List<CallbackExecution> findStuckPending(@Param("threshold") Instant threshold);
}
