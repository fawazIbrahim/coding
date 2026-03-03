package com.example.springintegration.service;

import com.example.springintegration.domain.MappedObject;

/**
 * Port for publishing a {@link MappedObject} to a Kafka topic.
 *
 * <p>The call blocks until the broker acknowledges the message or a
 * timeout/error occurs.</p>
 */
public interface KafkaProducerPort {

    /**
     * Sends the object to Kafka and waits for the broker acknowledgment.
     *
     * @param object the mapped object to publish
     * @return {@code true} if the message was successfully acknowledged;
     *         {@code false} if publishing failed
     */
    boolean sendAndWait(MappedObject object);
}
