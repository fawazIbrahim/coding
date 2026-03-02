package com.example.kafka.consumer;

import com.example.kafka.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService implements MessageListener<String, CloudEvent> {

    private final ObjectMapper objectMapper;

    /**
     * Invoked by the KafkaMessageListenerContainer registered in KafkaConfig.
     * On failure the container's error handler retries up to 3 times then
     * routes the record to the Dead Letter Topic.
     */
    @Override
    public void onMessage(ConsumerRecord<String, CloudEvent> record) {
        CloudEvent event = record.value();

        log.info("Received CloudEvent [id={}] type={} source={} from topic={} partition={} offset={}",
                event.getId(), event.getType(), event.getSource(),
                record.topic(), record.partition(), record.offset());

        Message message = extractData(event);
        processMessage(event, message);
    }

    private Message extractData(CloudEvent event) {
        try {
            byte[] data = event.getData() != null ? event.getData().toBytes() : null;
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("CloudEvent has no data: id=" + event.getId());
            }
            return objectMapper.readValue(data, Message.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize CloudEvent data", e);
        }
    }

    private void processMessage(CloudEvent event, Message message) {
        // Business logic goes here.
        // Throw a RuntimeException to trigger retries and eventual DLQ routing.
        log.info("Processing CloudEvent [id={}] with payload: {}", event.getId(), message);
    }
}
