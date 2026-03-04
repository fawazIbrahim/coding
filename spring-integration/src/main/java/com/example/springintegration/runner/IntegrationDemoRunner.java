package com.example.springintegration.runner;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.domain.ProcessingResult;
import com.example.springintegration.service.MessageSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runs on application startup to demonstrate all three processing paths.
 *
 * <p>Remove or replace this class when integrating the flow with a real
 * trigger (REST endpoint, Kafka consumer, scheduler, etc.).</p>
 */
@Component
public class IntegrationDemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IntegrationDemoRunner.class);

    private final MessageSenderService messageSenderService;

    public IntegrationDemoRunner(MessageSenderService messageSenderService) {
        this.messageSenderService = messageSenderService;
    }

    @Override
    public void run(String... args) {
        log.info("=== Starting integration flow demo ===");

        sendAndLog("type1", "order-payload-001");
        sendAndLog("type2", "event-payload-002");
        sendAndLog("type3", "query-payload-003");
        sendAndLog("unknown", "bad-payload");   // exercises the error path

        log.info("=== Demo complete ===");
    }

    private void sendAndLog(String type, Object payload) {
        log.info("--- Sending [{}] ---", type);
        ProcessingResult result = messageSenderService.send(new IntegrationMessage(type, payload));
        if (result.isSuccess()) {
            log.info("[{}] SUCCESS  data={}", type, result.getData());
        } else {
            log.warn("[{}] FAILURE  error={}", type, result.getErrorMessage());
        }
    }
}
