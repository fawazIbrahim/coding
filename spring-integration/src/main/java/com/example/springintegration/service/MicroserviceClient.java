package com.example.springintegration.service;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.exception.NonRetryableServiceException;
import com.example.springintegration.exception.RetryableServiceException;

/**
 * Port for calling a remote microservice.
 *
 * <p>Implementations are responsible for translating HTTP status codes into
 * the appropriate exception type:</p>
 * <ul>
 *   <li>{@link RetryableServiceException} for 5xx or 429 responses</li>
 *   <li>{@link NonRetryableServiceException} for other 4xx responses</li>
 * </ul>
 *
 * <p>This interface is used in both the Type1 and Type3 processing paths.</p>
 */
public interface MicroserviceClient {

    /**
     * Exchanges the given message with the remote microservice.
     *
     * @param message the outbound integration message
     * @return the raw response payload from the microservice
     * @throws RetryableServiceException    if the service returns a 5xx or 429 status
     * @throws NonRetryableServiceException if the service returns another error status
     */
    Object exchange(IntegrationMessage message)
        throws RetryableServiceException, NonRetryableServiceException;
}
