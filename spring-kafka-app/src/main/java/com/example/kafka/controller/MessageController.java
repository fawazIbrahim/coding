package com.example.kafka.controller;

import com.example.kafka.model.Message;
import com.example.kafka.producer.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final KafkaProducerService producerService;

    /**
     * Accepts a Message payload, wraps it in a CloudEvent, and queues it to Kafka.
     *
     * Example request:
     * POST /api/messages
     * { "content": "hello", "source": "my-service" }
     */
    @PostMapping
    public ResponseEntity<String> send(@RequestBody Message message) {
        if (message.getId() == null || message.getId().isBlank()) {
            message.setId(UUID.randomUUID().toString());
        }
        producerService.send(message);
        return ResponseEntity.accepted().body("CloudEvent queued: " + message.getId());
    }
}
