package com.example.kafka.config;

import com.example.kafka.consumer.KafkaConsumerService;
import com.example.kafka.consumer.KafkaDlqConsumerService;
import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${kafka.topic.main}")
    private String mainTopic;

    @Value("${kafka.topic.dlq}")
    private String dlqTopic;

    // -------------------------------------------------------------------------
    // Producer configuration
    // -------------------------------------------------------------------------

    @Bean
    public ProducerFactory<String, CloudEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // CloudEventSerializer encodes in binary content mode:
        // CE attributes → Kafka headers, data bytes → record value.
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, CloudEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // -------------------------------------------------------------------------
    // Consumer configuration
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, CloudEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // CloudEventDeserializer handles both binary and structured content modes.
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // -------------------------------------------------------------------------
    // DLQ error handler — retries 3 times with 2-second intervals,
    // then publishes the failed record to <topic-name>.DLT
    // -------------------------------------------------------------------------

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, CloudEvent> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        FixedBackOff backOff = new FixedBackOff(2000L, 3L);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    // -------------------------------------------------------------------------
    // Programmatic listener containers (no @KafkaListener annotations)
    // -------------------------------------------------------------------------

    @Bean
    public KafkaMessageListenerContainer<String, CloudEvent> mainListenerContainer(
            KafkaConsumerService consumerService,
            DefaultErrorHandler errorHandler) {
        ContainerProperties containerProps = new ContainerProperties(mainTopic);
        containerProps.setMessageListener(consumerService);

        KafkaMessageListenerContainer<String, CloudEvent> container =
                new KafkaMessageListenerContainer<>(consumerFactory(), containerProps);
        container.setCommonErrorHandler(errorHandler);
        return container;
    }

    @Bean
    public KafkaMessageListenerContainer<String, CloudEvent> dlqListenerContainer(
            KafkaDlqConsumerService dlqConsumerService,
            DefaultErrorHandler errorHandler) {
        ContainerProperties containerProps = new ContainerProperties(dlqTopic);
        // Override the group ID so the DLQ consumer belongs to a separate group
        containerProps.setGroupId(groupId + "-dlq");
        containerProps.setMessageListener(dlqConsumerService);

        KafkaMessageListenerContainer<String, CloudEvent> container =
                new KafkaMessageListenerContainer<>(consumerFactory(), containerProps);
        container.setCommonErrorHandler(errorHandler);
        return container;
    }
}
