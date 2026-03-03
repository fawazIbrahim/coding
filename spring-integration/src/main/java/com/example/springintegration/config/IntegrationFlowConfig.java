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
 *   MessageProcessingGateway  (sync – blocks until reply)
 *         │
 *   mainInputChannel  (DirectChannel – sender thread runs the whole flow)
 *         │
 *      [Router] ──────────────────────────────────┐
 *          │ type1           │ type2   │ type3     │ unknown
 *          ▼                 ▼         ▼           ▼
 *   type1Channel      type2Channel  type3Channel  unknownChannel
 *          │                 │         │
 *   [type1Flow]       [type2Flow]  [type3Flow]
 *    transform x2      transform x2  transform x1
 *          │                 │         │
 *          └────────────────►│◄────────┘
 *                            ▼
 *                   ProcessingResult (reply via replyChannel header)
 * </pre>
 *
 * <h2>Why transform() instead of handle()</h2>
 * {@code handle()} wraps a {@code MessageHandler} (void) and discards the return
 * value. {@code transform()} wraps a {@code GenericTransformer<S,T>} whose return
 * value IS the next message payload – this is the correct primitive for any step
 * that must produce a result.
 *
 * <h2>Retry strategy (type1 and type3 microservice calls)</h2>
 * <ul>
 *   <li>{@link RetryableServiceException} (5xx / 429): retried up to 3 times
 *       (4 total attempts). After exhaustion → recovery via {@link ErrorHandlerService}.</li>
 *   <li>Any other exception: not retried – recovery fires immediately.</li>
 * </ul>
 * Recovery always returns {@link ProcessingResult#failure}, so every step downstream
 * operates on a uniform {@code ProcessingResult} type.
 */
@Configuration
@EnableIntegration
@IntegrationComponentScan(basePackages = "com.example.springintegration")
public class IntegrationFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(IntegrationFlowConfig.class);

    /** 1 initial attempt + 3 retries = 4 total, as required by the spec. */
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
    // Channels
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The single DirectChannel that the gateway writes to.
     * DirectChannel dispatches synchronously on the sender's thread, so
     * {@code gateway.process()} blocks until the entire flow finishes.
     */
    @Bean
    public DirectChannel mainInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public DirectChannel type1Channel() {
        return new DirectChannel();
    }

    @Bean
    public DirectChannel type2Channel() {
        return new DirectChannel();
    }

    @Bean
    public DirectChannel type3Channel() {
        return new DirectChannel();
    }

    @Bean
    public DirectChannel unknownChannel() {
        return new DirectChannel();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main flow – router only
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reads from {@code mainInputChannel} and routes to the typed channel.
     * The {@code replyChannel} header set by the gateway is preserved through
     * the routing so each type flow can send its result directly back to the caller.
     */
    @Bean
    public IntegrationFlow mainFlow() {
        return IntegrationFlow
            .from(mainInputChannel())
            .<IntegrationMessage, String>route(
                msg -> resolveRoute(msg),
                mapping -> mapping
                    .channelMapping("type1", "type1Channel")
                    .channelMapping("type2", "type2Channel")
                    .channelMapping("type3", "type3Channel")
                    .channelMapping("unknown", "unknownChannel")
            )
            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type1 flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Type1 processing:
     * <ol>
     *   <li>Call the remote microservice.
     *       Wraps success in {@link ProcessingResult#success}.
     *       On 5xx/429 → retried up to 3 times; on other error → recovery fires
     *       immediately. Both paths produce {@link ProcessingResult#failure}.</li>
     *   <li>If step 1 failed, short-circuit. Otherwise call
     *       {@link Type1PostProcessingService}; on exception → error handler.</li>
     * </ol>
     */
    @Bean
    public IntegrationFlow type1Flow() {
        return IntegrationFlow
            .from(type1Channel())

            // Step 1 – microservice call with retry.
            // transform() return value becomes the next message payload.
            // Recovery callback also returns ProcessingResult so the type is uniform.
            .transform(
                (IntegrationMessage payload) -> {
                    log.debug("Type1: calling microservice, payload={}", payload.getPayload());
                    Object result = microserviceClient.exchange(payload);
                    return ProcessingResult.success(result);
                },
                spec -> spec.advice(createMicroserviceRetryAdvice())
            )

            // Step 2 – post-process.
            // Input is always ProcessingResult (either from step 1 success or recovery).
            .transform(
                (ProcessingResult result) -> {
                    if (!result.isSuccess()) {
                        return result; // already a handled failure – pass through
                    }
                    log.debug("Type1: post-processing result={}", result.getData());
                    try {
                        return type1PostProcessingService.process(result.getData());
                    } catch (Exception ex) {
                        log.warn("Type1: post-processing failed", ex);
                        return errorHandlerService.handle(ex);
                    }
                }
            )

            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type2 flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Type2 processing:
     * <ol>
     *   <li>Map the message to a {@link MappedObject} via
     *       {@link Type2MappingService}. On exception → error handler.</li>
     *   <li>If step 1 failed, short-circuit. Otherwise send to Kafka via
     *       {@link KafkaProducerPort#sendAndWait}; on {@code false} or exception
     *       → error handler. On success → return the {@link MappedObject}.</li>
     * </ol>
     */
    @Bean
    public IntegrationFlow type2Flow() {
        return IntegrationFlow
            .from(type2Channel())

            // Step 1 – map to MappedObject, wrap in ProcessingResult.
            .transform(
                (IntegrationMessage payload) -> {
                    log.debug("Type2: mapping payload={}", payload.getPayload());
                    try {
                        MappedObject mapped = type2MappingService.map(payload);
                        return ProcessingResult.success(mapped);
                    } catch (Exception ex) {
                        log.warn("Type2: mapping failed", ex);
                        return errorHandlerService.handle(ex);
                    }
                }
            )

            // Step 2 – publish to Kafka.
            .transform(
                (ProcessingResult result) -> {
                    if (!result.isSuccess()) {
                        return result; // already a handled failure – pass through
                    }
                    MappedObject mappedObject = (MappedObject) result.getData();
                    log.debug("Type2: sending to Kafka id={}", mappedObject.getId());
                    try {
                        boolean sent = kafkaProducerPort.sendAndWait(mappedObject);
                        if (!sent) {
                            log.warn("Type2: Kafka producer returned false");
                            return errorHandlerService.handle(
                                new RuntimeException("Kafka producer failed to acknowledge message"));
                        }
                        return ProcessingResult.success(mappedObject);
                    } catch (Exception ex) {
                        log.warn("Type2: Kafka producer threw exception", ex);
                        return errorHandlerService.handle(ex);
                    }
                }
            )

            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type3 flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Type3 processing:
     * <ol>
     *   <li>Call the remote microservice with the same retry policy as Type1.</li>
     *   <li>The microservice response is the final result.</li>
     * </ol>
     */
    @Bean
    public IntegrationFlow type3Flow() {
        return IntegrationFlow
            .from(type3Channel())

            .transform(
                (IntegrationMessage payload) -> {
                    log.debug("Type3: calling microservice, payload={}", payload.getPayload());
                    Object result = microserviceClient.exchange(payload);
                    return ProcessingResult.success(result);
                },
                spec -> spec.advice(createMicroserviceRetryAdvice())
            )

            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unknown-type flow
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public IntegrationFlow unknownTypeFlow() {
        return IntegrationFlow
            .from(unknownChannel())
            .transform(
                (IntegrationMessage payload) -> errorHandlerService.handle(
                    new IllegalArgumentException("Unknown or null message type: " + payload.getType()))
            )
            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Retry advice factory
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a fresh {@link RequestHandlerRetryAdvice} for each call site so that
     * type1 and type3 endpoints have independent retry state.
     *
     * <p>Works with both {@code transform()} and {@code handle()} endpoints because
     * {@link RequestHandlerRetryAdvice} targets {@code AbstractReplyProducingMessageHandler},
     * which is the base class for both {@code MessageTransformingHandler} and
     * {@code ServiceActivatingHandler}.</p>
     *
     * <p>Spring Retry behaviour:</p>
     * <ul>
     *   <li>{@link RetryableServiceException}: retried up to {@value #MAX_ATTEMPTS} times.
     *       After exhaustion, the recovery callback is invoked.</li>
     *   <li>Any other exception: {@code defaultValue=false} → policy declares it
     *       non-retryable, which triggers {@code TerminatedRetryException} internally,
     *       causing Spring Retry to call the recovery callback immediately.</li>
     * </ul>
     * Either way the recovery callback returns {@link ProcessingResult#failure} so the
     * downstream steps always receive a uniform {@code ProcessingResult}.
     */
    private RequestHandlerRetryAdvice createMicroserviceRetryAdvice() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(RetryableServiceException.class, true);
        // Everything else → defaultValue=false → non-retryable → recovery fires immediately

        SimpleRetryPolicy retryPolicy =
            new SimpleRetryPolicy(MAX_ATTEMPTS, retryableExceptions, true, false);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);

        RequestHandlerRetryAdvice advice = new RequestHandlerRetryAdvice();
        advice.setRetryTemplate(retryTemplate);
        advice.setRecoveryCallback(context -> {
            Throwable lastError = context.getLastThrowable();
            log.warn("Microservice call failed after {} attempt(s): {}",
                context.getRetryCount(), lastError.getMessage());
            return errorHandlerService.handle(lastError);
        });

        return advice;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private String resolveRoute(IntegrationMessage msg) {
        String type = msg.getType();
        if ("type1".equals(type) || "type2".equals(type) || "type3".equals(type)) {
            return type;
        }
        log.warn("Unrecognised message type '{}' – routing to error handler", type);
        return "unknown";
    }
}
