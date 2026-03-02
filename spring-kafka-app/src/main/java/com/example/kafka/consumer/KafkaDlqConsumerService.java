package com.example.kafka.consumer;

import com.example.kafka.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaDlqConsumerService implements MessageListener<String, CloudEvent> {

    private final ObjectMapper objectMapper;

    /**
     * Invoked by the DLQ KafkaMessageListenerContainer registered in KafkaConfig.
     * Records arrive here after all retry attempts on the main topic are exhausted.
     * The original CloudEvent envelope is preserved, so all CE attributes are accessible.
     */
    @Override
    public void onMessage(ConsumerRecord<String, CloudEvent> record) {
        CloudEvent event = record.value();
        String exceptionMessage = extractHeader(record, KafkaHeaders.EXCEPTION_MESSAGE);

        log.error("DLQ received CloudEvent [id={}] type={} source={} from topic={} partition={} offset={} | error={}",
                event.getId(), event.getType(), event.getSource(),
                record.topic(), record.partition(), record.offset(), exceptionMessage);

        handleDeadLetter(event, exceptionMessage);
    }

    private void handleDeadLetter(CloudEvent event, String exceptionMessage) {
        // Implement your dead-letter handling strategy here:
        // - Persist to a database for manual inspection
        // - Trigger an alert / notification
        // - Attempt a different remediation path
        try {
            Message message = event.getData() != null
                    ? objectMapper.readValue(event.getData().toBytes(), Message.class)
                    : null;
            log.warn("Dead-letter handling for CloudEvent [id={}] payload={} | reason={}",
                    event.getId(), message, exceptionMessage);
        } catch (Exception e) {
            log.warn("Dead-letter handling for CloudEvent [id={}] (could not deserialize data) | reason={}",
                    event.getId(), exceptionMessage);
        }
    }

    private String extractHeader(ConsumerRecord<?, ?> record, String headerKey) {
        Header header = record.headers().lastHeader(headerKey);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
