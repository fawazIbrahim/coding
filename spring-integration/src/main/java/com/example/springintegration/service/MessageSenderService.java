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
 * Application-level entry point for sending messages into the integration pipeline.
 *
 * <p>Uses Spring Integration's {@link MessagingTemplate#sendAndReceive} to dispatch
 * the message on the {@code mainInputChannel} and block until the flow returns a
 * reply. The template internally attaches a temporary {@code DirectChannel} as the
 * {@code replyChannel} header, which the last transformer in each type-flow uses to
 * route its result back to the caller — all on the same thread.</p>
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
     * @param message must have a non-null {@code type} field ("type1", "type2", or "type3")
     * @return the result of the processing pipeline; never {@code null}
     */
    public ProcessingResult send(IntegrationMessage message) {
        log.info("Sending message: {}", message);

        Message<IntegrationMessage> request = MessageBuilder.withPayload(message).build();
        Message<?> reply = messagingTemplate.sendAndReceive(mainInputChannel, request);

        if (reply == null) {
            log.error("No reply received for message type '{}' (timeout or channel misconfiguration)",
                message.getType());
            return ProcessingResult.failure("No reply received");
        }

        ProcessingResult result = (ProcessingResult) reply.getPayload();
        log.info("Processing completed – type='{}' result={}", message.getType(), result);
        return result;
    }
}
