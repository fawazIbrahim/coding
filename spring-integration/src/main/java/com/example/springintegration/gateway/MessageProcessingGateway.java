package com.example.springintegration.gateway;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.domain.ProcessingResult;
import org.springframework.integration.annotation.MessagingGateway;

/**
 * Spring Integration messaging gateway.
 *
 * <p>Provides a synchronous, type-safe entry point into the integration flow.
 * The call blocks until the entire processing pipeline completes and always
 * returns a {@link ProcessingResult} – success or handled failure.</p>
 *
 * <p>The underlying channel is a {@link org.springframework.integration.channel.DirectChannel},
 * which means the sender thread is the one executing the entire flow.</p>
 */
@MessagingGateway(defaultRequestChannel = "mainInputChannel")
public interface MessageProcessingGateway {

    /**
     * Sends the message into the integration channel and waits for the result.
     *
     * @param message the message to process
     * @return the outcome of the processing pipeline
     */
    ProcessingResult process(IntegrationMessage message);
}
