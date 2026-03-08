package com.example.springintegration.config;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.domain.IntermediateResult;
import com.example.springintegration.domain.MappedObject;
import com.example.springintegration.domain.ProcessingResult;
import com.example.springintegration.exception.RetryableServiceException;
import com.example.springintegration.service.KafkaProducerPort;
import com.example.springintegration.service.MicroserviceClient;
import com.example.springintegration.service.Type1PostProcessingService;
import com.example.springintegration.service.Type2MappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 *   MessageSenderService  (MessagingTemplate.sendAndReceive – blocks until reply)
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
 * value IS the next message payload.
 *
 * <h2>Exception strategy</h2>
 * Every transform step is written as pure business logic that throws on failure.
 * Because all channels are {@link DirectChannel} (synchronous), an exception
 * thrown in any step unwinds the call stack immediately — no further steps are
 * invoked. The single error-handling boundary lives in
 * {@link com.example.springintegration.service.MessageSenderService#send}, which
 * converts any flow-level exception to {@link ProcessingResult#failure}.
 *
 * <p>Microservice calls (type1 step1, type3) carry a
 * {@code createMicroserviceRetryAdvice()} that retries on
 * {@link RetryableServiceException} (5xx / 429) up to {@value #MAX_ATTEMPTS}
 * total attempts. After exhaustion, or on any non-retryable exception, the advice
 * re-throws and the exception propagates to {@code MessageSenderService}.</p>
 */
@Configuration
@EnableIntegration
public class IntegrationFlowConfig {

    private static final Logger log = LoggerFactory.getLogger(IntegrationFlowConfig.class);

    /** 1 initial attempt + 3 retries = 4 total, as required by the spec. */
    private static final int MAX_ATTEMPTS = 4;

    private final MicroserviceClient microserviceClient;
    private final Type1PostProcessingService type1PostProcessingService;
    private final Type2MappingService type2MappingService;
    private final KafkaProducerPort kafkaProducerPort;

    public IntegrationFlowConfig(
            MicroserviceClient microserviceClient,
            Type1PostProcessingService type1PostProcessingService,
            Type2MappingService type2MappingService,
            KafkaProducerPort kafkaProducerPort) {
        this.microserviceClient = microserviceClient;
        this.type1PostProcessingService = type1PostProcessingService;
        this.type2MappingService = type2MappingService;
        this.kafkaProducerPort = kafkaProducerPort;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Channels
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The single DirectChannel that {@link com.example.springintegration.service.MessageSenderService}
     * writes to via {@link org.springframework.integration.core.MessagingTemplate#sendAndReceive}.
     * DirectChannel is synchronous: the calling thread executes the entire flow and
     * blocks until the last transformer sends a reply to the temporary reply channel.
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

    @Bean
    public IntegrationFlow mainFlow() {
        return IntegrationFlow
            .from(mainInputChannel())
            .<IntegrationMessage, String>route(
                msg -> resolveRoute(msg),
                mapping -> mapping
                    .channelMapping("type1",   "type1Channel")
                    .channelMapping("type2",   "type2Channel")
                    .channelMapping("type3",   "type3Channel")
                    .channelMapping("unknown", "unknownChannel")
            )
            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type1 flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Step 1 – microservice call guarded by {@code createMicroserviceRetryAdvice()}.
     *          Returns {@link IntermediateResult} on success; re-throws on exhaustion
     *          or non-retryable error so the exception propagates to
     *          {@link com.example.springintegration.service.MessageSenderService}.
     *
     * Step 2 – post-processing with clean natural types; any exception propagates.
     */
    @Bean
    public IntegrationFlow type1Flow() {
        return IntegrationFlow
            .from(type1Channel())

            // Step 1: microservice call with retry – throws on failure.
            .transform(
                (IntegrationMessage payload) -> {
                    log.debug("Type1 step1: calling microservice payload={}", payload.getPayload());
                    return new IntermediateResult(microserviceClient.exchange(payload));
                },
                spec -> spec.advice(createMicroserviceRetryAdvice())
            )

            // Step 2: post-processing – input is always IntermediateResult (step 1 throws on error).
            .transform(
                (IntermediateResult ir) -> {
                    log.debug("Type1 step2: post-processing ir={}", ir);
                    return type1PostProcessingService.process(ir.getRawData());
                }
            )

            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type2 flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Step 1 – maps {@link IntegrationMessage} to {@link MappedObject}; throws on failure.
     * Step 2 – publishes to Kafka; throws if {@code sendAndWait} returns {@code false}
     *          or raises an exception.  Both exceptions propagate to
     *          {@link com.example.springintegration.service.MessageSenderService}.
     */
    @Bean
    public IntegrationFlow type2Flow() {
        return IntegrationFlow
            .from(type2Channel())

            // Step 1: map to MappedObject – throws on failure.
            .transform(
                (IntegrationMessage payload) -> {
                    log.debug("Type2 step1: mapping payload={}", payload.getPayload());
                    return type2MappingService.map(payload);
                }
            )

            // Step 2: Kafka publish – throws on failure.
            .transform(
                (MappedObject mapped) -> {
                    log.debug("Type2 step2: sending to Kafka id={}", mapped.getId());
                    boolean sent = kafkaProducerPort.sendAndWait(mapped);
                    if (!sent) {
                        throw new RuntimeException("Kafka producer failed to acknowledge message");
                    }
                    return ProcessingResult.success(mapped);
                }
            )

            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Type3 flow
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Single step – microservice call with the same retry policy as Type1.
     * The microservice response IS the final result.
     */
    @Bean
    public IntegrationFlow type3Flow() {
        return IntegrationFlow
            .from(type3Channel())

            .transform(
                (IntegrationMessage payload) -> {
                    log.debug("Type3 step1: calling microservice payload={}", payload.getPayload());
                    return ProcessingResult.success(microserviceClient.exchange(payload));
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
            .<IntegrationMessage, ProcessingResult>transform(
                payload -> {
                    throw new IllegalArgumentException(
                        "Unknown or null message type: " + payload.getType());
                }
            )
            .get();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Advice factories
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retry advice for remote microservice calls (type1 step1 and type3).
     *
     * <ul>
     *   <li>{@link RetryableServiceException} (5xx/429): retried up to
     *       {@value #MAX_ATTEMPTS} total attempts, then re-thrown.</li>
     *   <li>Any other exception: {@code defaultValue=false} → treated as
     *       non-retryable; Spring Retry re-throws immediately.</li>
     * </ul>
     * All exceptions (including {@code ExhaustedRetryException} after the last
     * attempt) propagate to
     * {@link com.example.springintegration.service.MessageSenderService#send},
     * which unwraps the cause chain and converts it to
     * {@link ProcessingResult#failure}.
     */
    private RequestHandlerRetryAdvice createMicroserviceRetryAdvice() {
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(RetryableServiceException.class, true);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(
            new SimpleRetryPolicy(MAX_ATTEMPTS, retryable, true, false));

        RequestHandlerRetryAdvice advice = new RequestHandlerRetryAdvice();
        advice.setRetryTemplate(retryTemplate);
        // No recovery callback – exhausted retries re-throw to MessageSenderService.
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
