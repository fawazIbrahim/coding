package com.example.springintegration.service;

import com.example.springintegration.domain.ProcessingResult;
import com.example.springintegration.exception.NonRetryableServiceException;
import com.example.springintegration.exception.RetryableServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.stereotype.Service;

/**
 * Central error handler that is invoked from all processing paths when an
 * unrecoverable failure occurs.
 *
 * <p>This service decides the final {@link ProcessingResult} when the normal
 * processing path cannot complete successfully. It inspects the exception type
 * to provide a meaningful error message and apply any business-specific
 * fallback logic.</p>
 */
@Service
public class ErrorHandlerService {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlerService.class);

    /**
     * Converts a throwable into a {@link ProcessingResult} that describes
     * the failure. Extend this method with business-specific fallback behaviour
     * (e.g., returning a cached response, publishing a dead-letter event, etc.).
     *
     * @param throwable the exception that triggered the error path
     * @return a failed {@link ProcessingResult} with a descriptive message
     */
    public ProcessingResult handle(Throwable throwable) {
        log.error("Processing failed – delegating to error handler: {}", throwable.getMessage(), throwable);

        // Spring Retry wraps the last exception in ExhaustedRetryException once
        // all retry attempts are consumed.
        if (throwable instanceof ExhaustedRetryException exhausted && exhausted.getCause() != null) {
            return handle(exhausted.getCause());
        }

        if (throwable instanceof RetryableServiceException rse) {
            log.error("Microservice unavailable after retries (HTTP {})", rse.getStatusCode());
            return ProcessingResult.failure(
                "Microservice unavailable after retries – HTTP " + rse.getStatusCode());
        }

        if (throwable instanceof NonRetryableServiceException nrse) {
            log.error("Microservice call rejected (HTTP {})", nrse.getStatusCode());
            return ProcessingResult.failure(
                "Microservice call failed – HTTP " + nrse.getStatusCode());
        }

        // Generic fallback for all other exceptions
        return ProcessingResult.failure("Processing failed: " + throwable.getMessage());
    }
}
