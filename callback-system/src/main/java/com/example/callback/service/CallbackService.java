package com.example.callback.service;

import com.example.callback.api.dto.CallbackRequest;
import com.example.callback.api.dto.CallbackResponse;
import com.example.callback.domain.CallbackExecution;
import com.example.callback.domain.CallbackTarget;
import com.example.callback.domain.ExecutionStatus;
import com.example.callback.repository.CallbackExecutionRepository;
import com.example.callback.repository.CallbackTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CallbackService {

    private static final Logger log = LoggerFactory.getLogger(CallbackService.class);

    private final CallbackTargetRepository targetRepository;
    private final CallbackExecutionRepository executionRepository;

    public CallbackService(CallbackTargetRepository targetRepository,
                           CallbackExecutionRepository executionRepository) {
        this.targetRepository = targetRepository;
        this.executionRepository = executionRepository;
    }

    /**
     * Validates the target, persists the execution as PENDING, and returns.
     * The DB poller will pick it up within one poll interval (default 1 s).
     */
    @Transactional
    public CallbackResponse createCallback(CallbackRequest request) {
        CallbackTarget target = targetRepository.findByNameAndEnabledTrue(request.target())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Target not found or disabled: " + request.target()));

        CallbackExecution execution = new CallbackExecution();
        execution.setCallbackType(request.type());
        execution.setData(request.data());
        execution.setTarget(target);
        execution.setMaxAttempts(target.getMaxRetries() + 1);
        execution.setStatus(ExecutionStatus.PENDING);
        execution = executionRepository.save(execution);

        log.info("Created execution={} type={} target={}", execution.getId(), request.type(), request.target());
        return toResponse(execution);
    }

    @Transactional(readOnly = true)
    public CallbackResponse getExecution(UUID executionId) {
        CallbackExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Execution not found: " + executionId));
        return toResponse(execution);
    }

    private CallbackResponse toResponse(CallbackExecution e) {
        return new CallbackResponse(
                e.getId(),
                e.getCallbackType(),
                e.getTarget().getName(),
                e.getStatus(),
                e.getAttemptCount(),
                e.getMaxAttempts(),
                e.getNextRetryAt(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
