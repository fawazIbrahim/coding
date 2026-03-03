package com.example.springintegration.service;

import com.example.springintegration.domain.ProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Performs post-processing operations on the result returned by the remote
 * microservice in the Type1 processing path.
 *
 * <p>If this service throws any exception, the caller (the integration flow)
 * will delegate to {@link ErrorHandlerService}.</p>
 */
@Service
public class Type1PostProcessingService {

    private static final Logger log = LoggerFactory.getLogger(Type1PostProcessingService.class);

    /**
     * Applies business operations to the microservice response.
     *
     * @param microserviceResult the raw response from the remote microservice
     * @return a {@link ProcessingResult} wrapping the final outcome
     * @throws RuntimeException if a processing error occurs
     */
    public ProcessingResult process(Object microserviceResult) {
        log.debug("Type1 post-processing result: {}", microserviceResult);
        // TODO: replace with actual business logic
        return ProcessingResult.success(microserviceResult);
    }
}
