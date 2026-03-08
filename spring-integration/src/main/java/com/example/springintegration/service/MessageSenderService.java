package com.example.springintegration.service;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.domain.ProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Entry point for submitting messages into the integration pipeline.
 *
 * <h2>Exception strategy</h2>
 * Every transform step in every flow is written as pure business logic that
 * throws on failure. Because all channels are {@link DirectChannel} (synchronous),
 * an exception thrown in any step unwinds the call stack immediately — no further
 * steps are invoked — and surfaces here as an exception from
 * {@link MessagingTemplate#sendAndReceive}.
 *
 * <p>This single catch converts <em>any</em> flow-level exception into a
 * {@link ProcessingResult#failure}, making it the sole error-handling boundary.
 * The alternative — catching inside every lambda and returning a failure — forces
 * all subsequent steps to execute (just to pass the failure through), which is
 * wasteful in long chains.</p>
 */
@Service
public class MessageSenderService {

    private static final Logger log = LoggerFactory.getLogger(MessageSenderService.class);

    private final MessagingTemplate messagingTemplate;
    private final DirectChannel mainInputChannel;

    public MessageSenderService(DirectChannel mainInputChannel) {
        this.mainInputChannel = mainInputChannel;
        this.messagingTemplate = new MessagingTemplate();
    }

    /**
     * Sends {@code message} through the integration channel and blocks until
     * the flow returns a {@link ProcessingResult}.
     *
     * @param message must have a non-null {@code type} field
     * @return the result of the pipeline; never {@code null}
     */
    public ProcessingResult send(IntegrationMessage message) {
        log.info("Sending message: {}", message);
        Message<IntegrationMessage> request = MessageBuilder.withPayload(message).build();
        try {
            Message<?> reply = messagingTemplate.sendAndReceive(mainInputChannel, request);
            if (reply == null) {
                return ProcessingResult.failure("No reply received (timeout or misconfiguration)");
            }
            ProcessingResult result = (ProcessingResult) reply.getPayload();
            log.info("Processing completed – type='{}' result={}", message.getType(), result);
            return result;
        } catch (Exception ex) {
            // Any exception thrown by any step in the flow arrives here.
            // We unwrap Spring Integration / Spring Retry wrapper exceptions to
            // reach the root cause before delegating to the error handler.
            Throwable cause = unwrapCause(ex);
            log.error("Flow failed for type='{}': {}", message.getType(), cause.getMessage(), cause);
            return ProcessingResult.failure(cause.getMessage());
        }
    }

    /**
     * Walks the cause chain to strip Spring Integration wrapper exceptions
     * (e.g. {@code MessageHandlingException}, {@code MessageDeliveryException})
     * and Spring Retry's {@code ExhaustedRetryException}, exposing the original
     * business-level exception for logging and error-handler inspection.
     */
    private static Throwable unwrapCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
