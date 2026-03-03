package com.example.springintegration.config;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.domain.MappedObject;
import com.example.springintegration.domain.ProcessingResult;
import com.example.springintegration.exception.RetryableServiceException;
import com.example.springintegration.service.ErrorHandlerService;
import com.example.springintegration.service.KafkaProducerPort;
import com.example.springintegration.service.MicroserviceClient;
import com.example.springintegration.service.Type1PostProcessingService;
import com.example.springintegration.service.Type2MappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.handler.advice.RequestHandlerRetryAdvice;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Integration flow configuration.
 *
 * <h2>Architecture</h2>
 * <pre>
 *   MessageSenderService
 *         │
 *   MessageProcessingGateway  (sync, blocks until reply)
 *         │
 *   mainInputChannel  (DirectChannel – sender thread executes the whole flow)
 *         │
 *      [Router] ── type ──► type1SubFlow ─┐
 *                       ├─► type2SubFlow ─┤─► ProcessingResult (reply)
 *                       ├─► type3SubFlow ─┘
 *                       └─► unknown  ──► ErrorHandlerService
 * </pre>
 *
 * <h2>Retry strategy (type1 & type3 microservice calls)</h2>
 * <ul>
 *   <li>{@link RetryableServiceException} (5xx / 429) → retry up to 3 times
 *       (4 total attempts), then recovery via {@link ErrorHandlerService}</li>
 *   <li>{@link com.example.springintegration.exception.NonRetryableServiceException}
 *       → skip retries immediately, recovery via {@link ErrorHandlerService}</li>
 * </ul>
 */
@Configuration
@EnableIntegration
@IntegrationComponentScan(basePackages = "com.example.springintegration")
public class IntegrationFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(IntegrationFlowConfig.class);

    /** Total attempts = 1 initial + 3 retries, as required by the spec. */
    private static final int MAX_ATTEMPTS = 4;

    private final ErrorHandlerService errorHandlerService;
    private final MicroserviceClient microserviceClient;
    private final Type1PostProcessingService type1PostProcessingService;
    private final Type2MappingService type2MappingService;
    private final KafkaProducerPort kafkaProducerPort;

    public IntegrationFlowConfig(
            ErrorHandlerService errorHandlerService,
            MicroserviceClient microserviceClient,
            Type1PostProcessingService type1PostProcessingService,
            Type2MappingService type2MappingService,
            KafkaProducerPort kafkaProducerPort) {
        this.errorHandlerService = errorHandlerService;
        this.microserviceClient = microserviceClient;
        this.type1PostProcessingService = type1PostProcessingService;
        this.type2MappingService = type2MappingService;
        this.kafkaProducerPort = kafkaProducerPort;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Channel
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The single DirectChannel through which all messages are dispatched.
     * DirectChannel is synchronous: the calling thread executes the handler,
     * so {@code gateway.process()} blocks until the full flow completes.
     */
    @Bean
    public DirectChannel mainInputChannel() {
        return new DirectChannel();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Receives every message from {@code mainInputChannel} and routes it to
     * the correct sub-flow based on the {@code type} field.
     */
    @Bean
    public IntegrationFlow mainFlow() {
        return IntegrationFlow
            .from(mainInputChannel())
            .<IntegrationMessage, String>route(
                msg -> resolveRoute(msg),
                mapping -> mapping
                    .subFlowMapping("type1", f -> type1SubFlow(f))
                    .subFlowMapping("type2", f -> type2SubFlow(f))
                    .subFlowMapping("type3", f -> type3SubFlow(f))
                    // Unknown / null type → immediate error
                    .subFlowMapping("unknown", f -> f
                        .<IntegrationMessage, ProcessingResult>handle(
                            (payload, headers) -> errorHandlerService.handle(
                                new IllegalArgumentException(
                                    "Unknown or missing message type: " + payload.getType()))
                        )
                    )
            )
            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type1 sub-flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Type1 processing:
     * <ol>
     *   <li>Call the remote microservice with retry (5xx/429 → retry, other → direct recovery)</li>
     *   <li>On microservice success → pass result to {@link Type1PostProcessingService}</li>
     *   <li>On post-processing exception → {@link ErrorHandlerService}</li>
     * </ol>
     */
    private IntegrationFlow type1SubFlow(IntegrationFlow flow) {
        return flow
            // Step 1 – microservice call with retry advice
            .<IntegrationMessage, Object>handle(
                (payload, headers) -> {
                    log.debug("Type1: calling microservice for payload={}", payload.getPayload());
                    return microserviceClient.exchange(payload);
                },
                spec -> spec.advice(createMicroserviceRetryAdvice())
            )
            // Step 2 – post-process the microservice result
            // If step 1 recovery fired, the payload is already a ProcessingResult – pass it through.
            .<Object, ProcessingResult>handle(
                (payload, headers) -> {
                    if (payload instanceof ProcessingResult alreadyHandled) {
                        return alreadyHandled;
                    }
                    log.debug("Type1: post-processing microservice result: {}", payload);
                    try {
                        return type1PostProcessingService.process(payload);
                    } catch (Exception ex) {
                        log.warn("Type1: post-processing failed, delegating to error handler", ex);
                        return errorHandlerService.handle(ex);
                    }
                }
            );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type2 sub-flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Type2 processing:
     * <ol>
     *   <li>Map the message to a {@link MappedObject} via {@link Type2MappingService}</li>
     *   <li>On mapping exception → {@link ErrorHandlerService}</li>
     *   <li>On mapping success → publish via {@link KafkaProducerPort#sendAndWait}</li>
     *   <li>On publish failure (false or exception) → {@link ErrorHandlerService}</li>
     *   <li>On publish success → return the {@link MappedObject} as the final result</li>
     * </ol>
     */
    private IntegrationFlow type2SubFlow(IntegrationFlow flow) {
        return flow
            // Step 1 – map message to MappedObject
            .<IntegrationMessage, Object>handle(
                (payload, headers) -> {
                    log.debug("Type2: mapping message: {}", payload.getPayload());
                    try {
                        return type2MappingService.map(payload);
                    } catch (Exception ex) {
                        log.warn("Type2: mapping failed, delegating to error handler", ex);
                        return errorHandlerService.handle(ex);
                    }
                }
            )
            // Step 2 – send to Kafka
            // If step 1 failed, the payload is already a ProcessingResult – pass it through.
            .<Object, ProcessingResult>handle(
                (payload, headers) -> {
                    if (payload instanceof ProcessingResult alreadyHandled) {
                        return alreadyHandled;
                    }
                    MappedObject mappedObject = (MappedObject) payload;
                    log.debug("Type2: sending to Kafka: {}", mappedObject.getId());
                    try {
                        boolean sent = kafkaProducerPort.sendAndWait(mappedObject);
                        if (!sent) {
                            log.warn("Type2: Kafka producer returned false, delegating to error handler");
                            return errorHandlerService.handle(
                                new RuntimeException("Kafka producer failed to acknowledge message"));
                        }
                        return ProcessingResult.success(mappedObject);
                    } catch (Exception ex) {
                        log.warn("Type2: Kafka producer threw exception, delegating to error handler", ex);
                        return errorHandlerService.handle(ex);
                    }
                }
            );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type3 sub-flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Type3 processing:
     * <ol>
     *   <li>Call the remote microservice with the same retry logic as Type1</li>
     *   <li>On success → microservice result is the final result</li>
     *   <li>On failure (retries exhausted or non-retryable) → {@link ErrorHandlerService}</li>
     * </ol>
     */
    private IntegrationFlow type3SubFlow(IntegrationFlow flow) {
        return flow
            .<IntegrationMessage, ProcessingResult>handle(
                (payload, headers) -> {
                    log.debug("Type3: calling microservice for payload={}", payload.getPayload());
                    Object result = microserviceClient.exchange(payload);
                    return ProcessingResult.success(result);
                },
                spec -> spec.advice(createMicroserviceRetryAdvice())
            );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retry advice factory
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a {@link RequestHandlerRetryAdvice} configured as follows:
     * <ul>
     *   <li>{@link RetryableServiceException} → retried up to {@value #MAX_ATTEMPTS} total
     *       attempts (1 initial + 3 retries)</li>
     *   <li>Any other exception → not retried (treated as immediately exhausted)</li>
     *   <li>After exhaustion (both cases) → recovery via {@link ErrorHandlerService}</li>
     * </ul>
     *
     * <p>A new advice instance is created per call so that each endpoint has an
     * independent configuration.</p>
     */
    private RequestHandlerRetryAdvice createMicroserviceRetryAdvice() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        // Only RetryableServiceException triggers retries; everything else is non-retryable.
        retryableExceptions.put(RetryableServiceException.class, true);

        // traverseCauses=true  → inspect the cause chain for RetryableServiceException
        // defaultValue=false   → any exception NOT in the map is non-retryable
        SimpleRetryPolicy retryPolicy =
            new SimpleRetryPolicy(MAX_ATTEMPTS, retryableExceptions, true, false);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);

        RequestHandlerRetryAdvice advice = new RequestHandlerRetryAdvice();
        advice.setRetryTemplate(retryTemplate);
        // Recovery is called for both retries-exhausted AND non-retryable exceptions.
        advice.setRecoveryCallback(context -> {
            Throwable lastError = context.getLastThrowable();
            log.warn("Microservice call failed (attempt {}), delegating to error handler: {}",
                context.getRetryCount(), lastError.getMessage());
            return errorHandlerService.handle(lastError);
        });

        return advice;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the routing key for the given message.
     * Maps {@code null} or unrecognised type values to {@code "unknown"}.
     */
    private String resolveRoute(IntegrationMessage msg) {
        String type = msg.getType();
        if ("type1".equals(type) || "type2".equals(type) || "type3".equals(type)) {
            return type;
        }
        log.warn("Unrecognised message type '{}', routing to error handler", type);
        return "unknown";
    }
}
