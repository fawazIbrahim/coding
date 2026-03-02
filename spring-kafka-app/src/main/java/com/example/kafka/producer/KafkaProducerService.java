package com.example.kafka.producer;

import com.example.kafka.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, CloudEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.main}")
    private String mainTopic;

    @Value("${kafka.cloudevents.source}")
    private String eventSource;

    @Value("${kafka.cloudevents.type}")
    private String eventType;

    /**
     * Wraps the domain message in a CloudEvent envelope and sends it to the main topic.
     * The message ID becomes both the CE id and the Kafka record key.
     */
    public void send(Message message) {
        CloudEvent event = buildCloudEvent(message);

        CompletableFuture<SendResult<String, CloudEvent>> future =
                kafkaTemplate.send(mainTopic, event.getId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send CloudEvent [id={}]: {}", event.getId(), ex.getMessage());
            } else {
                log.info("Sent CloudEvent [id={}] type={} to topic={} partition={} offset={}",
                        event.getId(),
                        event.getType(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private CloudEvent buildCloudEvent(Message message) {
        try {
            byte[] dataBytes = objectMapper.writeValueAsBytes(message);

            return CloudEventBuilder.v1()
                    .withId(message.getId())
                    .withSource(URI.create(eventSource))
                    .withType(eventType)
                    .withDataContentType("application/json")
                    .withTime(OffsetDateTime.now())
                    .withData(dataBytes)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize message to CloudEvent", e);
        }
    }
}
