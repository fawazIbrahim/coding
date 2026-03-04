package com.example.springintegration.gateway;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.domain.ProcessingResult;
/**
 * Spring Integration messaging gateway.
 *
 * <p>Provides a synchronous, type-safe entry point into the integration flow.
 * The call blocks until the entire processing pipeline completes and always
 * returns a {@link ProcessingResult} – success or handled failure.</p>
 *
 * <p>The proxy for this interface is registered explicitly via
 * {@link org.springframework.integration.gateway.GatewayProxyFactoryBean} in
 * {@link com.example.springintegration.config.IntegrationFlowConfig} –
 * no annotation scanning is required.</p>
 */
public interface MessageProcessingGateway {

    /**
     * Sends the message into the integration channel and waits for the result.
     *
     * @param message the message to process
     * @return the outcome of the processing pipeline
     */
    ProcessingResult process(IntegrationMessage message);
}
