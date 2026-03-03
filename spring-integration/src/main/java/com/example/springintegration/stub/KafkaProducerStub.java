package com.example.springintegration.stub;

import com.example.springintegration.domain.MappedObject;
import com.example.springintegration.service.KafkaProducerPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link KafkaProducerPort}.
 *
 * <p><strong>Replace this with a real Kafka producer implementation</strong>
 * that sends the {@link MappedObject} to the appropriate topic and waits for
 * broker acknowledgment before returning.</p>
 */
@Component
public class KafkaProducerStub implements KafkaProducerPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerStub.class);

    @Override
    public boolean sendAndWait(MappedObject object) {
        log.info("[STUB] KafkaProducerPort.sendAndWait called with: {}", object);
        // Stub always reports success – no actual Kafka call is made.
        return true;
    }
}
