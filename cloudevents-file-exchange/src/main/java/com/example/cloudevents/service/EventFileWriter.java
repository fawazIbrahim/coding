package com.example.cloudevents.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.core.data.PojoCloudEventData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes CloudEvents to NDJSON files (one JSON object per line).
 *
 * <p>Supports three content modes determined by datacontenttype:
 * <ul>
 *   <li><b>application/json</b> – data is inlined as a JSON object in the CloudEvent line.</li>
 *   <li><b>application/xml</b>  – data is XML-serialized and stored base64-encoded (data_base64).</li>
 *   <li><b>text/* / binary</b>  – data is stored base64-encoded (data_base64).</li>
 * </ul>
 *
 * <p>File naming convention: {@code events-{tag}-{yyyyMMdd-HHmmss}.ndjson}
 */
@Slf4j
@Service
public class EventFileWriter {

    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;

    @Value("${events.output.dir:./events-output}")
    private String outputDir;

    public EventFileWriter(ObjectMapper jsonMapper, XmlMapper xmlMapper) {
        this.jsonMapper = jsonMapper;
        this.xmlMapper = xmlMapper;
    }

    // -------------------------------------------------------------------------
    // File management
    // -------------------------------------------------------------------------

    /**
     * Creates a new NDJSON file in the configured output directory.
     *
     * @param sourceTag short label embedded in the filename (e.g. "orders-service")
     */
    public Path createOutputFile(String sourceTag) throws IOException {
        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);

        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        String safeTag = sourceTag.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path file = dir.resolve("events-" + safeTag + "-" + timestamp + ".ndjson");
        log.info("Created output file: {}", file.toAbsolutePath());
        return file;
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /** Appends a single CloudEvent as one JSON line. */
    public void write(CloudEvent event, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(jsonMapper.writeValueAsString(event));
            writer.newLine();
        }
    }

    /** Appends a list of CloudEvents, one per line. */
    public void writeAll(List<CloudEvent> events, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (CloudEvent event : events) {
                writer.write(jsonMapper.writeValueAsString(event));
                writer.newLine();
            }
        }
        log.info("Wrote {} event(s) to {}", events.size(), file);
    }

    // -------------------------------------------------------------------------
    // CloudEvent builder helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a CloudEvent with {@code datacontenttype: application/json}.
     * The data object is serialized inline (not base64) in the JSON line.
     */
    public <T> CloudEvent jsonEvent(String id, String type, String source, T data)
            throws JsonProcessingException {
        return CloudEventBuilder.v1()
                .withId(id)
                .withType(type)
                .withSource(URI.create(source))
                .withTime(OffsetDateTime.now())
                .withDataContentType("application/json")
                .withData(PojoCloudEventData.wrap(data, jsonMapper::writeValueAsBytes))
                .build();
    }

    /**
     * Builds a CloudEvent with {@code datacontenttype: application/xml}.
     * The data object is XML-serialized; the resulting bytes are stored in
     * {@code data_base64} when the CloudEvent is written to JSON.
     */
    public <T> CloudEvent xmlEvent(String id, String type, String source, T data)
            throws JsonProcessingException {
        byte[] xmlBytes = xmlMapper.writeValueAsBytes(data);
        return CloudEventBuilder.v1()
                .withId(id)
                .withType(type)
                .withSource(URI.create(source))
                .withTime(OffsetDateTime.now())
                .withDataContentType("application/xml")
                .withData(BytesCloudEventData.wrap(xmlBytes))
                .build();
    }

    /**
     * Builds a CloudEvent with {@code datacontenttype: text/plain}.
     * The text is UTF-8-encoded; stored in {@code data_base64}.
     */
    public CloudEvent textEvent(String id, String type, String source, String text) {
        return CloudEventBuilder.v1()
                .withId(id)
                .withType(type)
                .withSource(URI.create(source))
                .withTime(OffsetDateTime.now())
                .withDataContentType("text/plain")
                .withData(BytesCloudEventData.wrap(text.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    /**
     * Builds a CloudEvent with an arbitrary content type and raw byte payload.
     * Useful for binary formats (Avro, Protobuf, images, …).
     * The bytes are stored in {@code data_base64}.
     */
    public CloudEvent binaryEvent(String id, String type, String source,
                                   String contentType, byte[] data) {
        return CloudEventBuilder.v1()
                .withId(id)
                .withType(type)
                .withSource(URI.create(source))
                .withTime(OffsetDateTime.now())
                .withDataContentType(contentType)
                .withData(BytesCloudEventData.wrap(data))
                .build();
    }
}
