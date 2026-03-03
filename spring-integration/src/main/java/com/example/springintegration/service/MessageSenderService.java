package com.example.springintegration.service;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.domain.ProcessingResult;
import com.example.springintegration.gateway.MessageProcessingGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application-level facade for sending messages into the integration pipeline.
 *
 * <p>This is the primary entry point for callers. It delegates to the
 * {@link MessageProcessingGateway} and blocks until the entire integration
 * flow completes, returning the final {@link ProcessingResult}.</p>
 */
@Service
public class MessageSenderService {

    private static final Logger log = LoggerFactory.getLogger(MessageSenderService.class);

    private final MessageProcessingGateway gateway;

    public MessageSenderService(MessageProcessingGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Submits a message to the integration channel and waits for the result.
     *
     * @param message the message to process (must have a non-null {@code type} field)
     * @return the {@link ProcessingResult} – never {@code null}
     */
    public ProcessingResult send(IntegrationMessage message) {
        log.info("Sending message: {}", message);
        ProcessingResult result = gateway.process(message);
        log.info("Processing completed for message type '{}': {}", message.getType(), result);
        return result;
    }
}
