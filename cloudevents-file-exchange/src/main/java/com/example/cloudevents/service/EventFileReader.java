package com.example.cloudevents.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads CloudEvents from an NDJSON file (one CloudEvent JSON object per line).
 *
 * <p>The CloudEventJacksonModule (registered in JacksonConfig) handles the
 * deserialization transparently:
 * <ul>
 *   <li>Lines with an inline {@code data} object  → parsed as {@link io.cloudevents.jackson.JsonCloudEventData}</li>
 *   <li>Lines with a {@code data_base64} field    → parsed as {@link io.cloudevents.core.data.BytesCloudEventData}</li>
 * </ul>
 * Use {@link ContentTypeHandler} to extract the typed payload after reading.
 */
@Slf4j
@Service
public class EventFileReader {

    private final ObjectMapper jsonMapper;

    public EventFileReader(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Reads all CloudEvents from the given NDJSON file.
     * Blank lines are skipped; malformed lines throw an unchecked exception.
     */
    public List<CloudEvent> readAll(Path file) throws IOException {
        log.info("Reading events from: {}", file.toAbsolutePath());
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            List<CloudEvent> events = lines
                    .filter(line -> !line.isBlank())
                    .map(this::parseLine)
                    .toList();
            log.info("Read {} event(s) from {}", events.size(), file.getFileName());
            return events;
        }
    }

    private CloudEvent parseLine(String line) {
        try {
            return jsonMapper.readValue(line, CloudEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse CloudEvent line: " + line, e);
        }
    }
}
