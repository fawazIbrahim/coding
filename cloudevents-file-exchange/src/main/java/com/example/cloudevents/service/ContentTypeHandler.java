package com.example.cloudevents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.data.BytesCloudEventData;
import io.cloudevents.jackson.JsonCloudEventData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Extracts typed payloads from a {@link CloudEvent} based on its
 * {@code datacontenttype} attribute.
 *
 * <p>How the data field is stored (set by {@link EventFileWriter}):
 * <table>
 *   <tr><th>datacontenttype</th><th>In the NDJSON file</th><th>CloudEventData type</th></tr>
 *   <tr><td>application/json</td><td>inline {@code data} object</td><td>JsonCloudEventData</td></tr>
 *   <tr><td>application/xml</td><td>{@code data_base64}</td><td>BytesCloudEventData</td></tr>
 *   <tr><td>text/*</td><td>{@code data_base64}</td><td>BytesCloudEventData</td></tr>
 *   <tr><td>anything else</td><td>{@code data_base64}</td><td>BytesCloudEventData</td></tr>
 * </table>
 */
@Slf4j
@Component
public class ContentTypeHandler {

    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;

    public ContentTypeHandler(ObjectMapper jsonMapper, XmlMapper xmlMapper) {
        this.jsonMapper = jsonMapper;
        this.xmlMapper = xmlMapper;
    }

    // -------------------------------------------------------------------------
    // Content-type predicates
    // -------------------------------------------------------------------------

    public boolean isJson(CloudEvent event) {
        String ct = event.getDataContentType();
        return ct != null && (ct.equals("application/json") || ct.endsWith("+json"));
    }

    public boolean isXml(CloudEvent event) {
        String ct = event.getDataContentType();
        return ct != null && (ct.equals("application/xml") || ct.equals("text/xml") || ct.endsWith("+xml"));
    }

    public boolean isText(CloudEvent event) {
        String ct = event.getDataContentType();
        return ct != null && ct.startsWith("text/");
    }

    // -------------------------------------------------------------------------
    // Extraction helpers
    // -------------------------------------------------------------------------

    /**
     * Deserializes the event data as JSON into the given type.
     * Works whether the data was stored inline (JsonCloudEventData) or as
     * base64 bytes (BytesCloudEventData).
     */
    public <T> Optional<T> extractJson(CloudEvent event, Class<T> type) {
        if (event.getData() == null) return Optional.empty();
        try {
            if (event.getData() instanceof JsonCloudEventData jsonData) {
                return Optional.of(jsonMapper.treeToValue(jsonData.getNode(), type));
            }
            return Optional.of(jsonMapper.readValue(event.getData().toBytes(), type));
        } catch (Exception e) {
            log.error("Failed to extract JSON from event {}: {}", event.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Deserializes the event data as XML into the given type.
     * The bytes are expected to be UTF-8-encoded XML.
     */
    public <T> Optional<T> extractXml(CloudEvent event, Class<T> type) {
        if (event.getData() == null) return Optional.empty();
        try {
            byte[] bytes = event.getData().toBytes();
            return Optional.of(xmlMapper.readValue(bytes, type));
        } catch (Exception e) {
            log.error("Failed to extract XML from event {}: {}", event.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the event data as a UTF-8 string.
     * Suitable for text/plain and similar content types.
     */
    public Optional<String> extractText(CloudEvent event) {
        if (event.getData() == null) return Optional.empty();
        try {
            byte[] bytes = event.getData().toBytes();
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to extract text from event {}: {}", event.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Returns the raw bytes of the event data.
     * Use for binary content types (application/octet-stream, Avro, Protobuf, …).
     */
    public Optional<byte[]> extractBytes(CloudEvent event) {
        if (event.getData() == null) return Optional.empty();
        try {
            return Optional.of(event.getData().toBytes());
        } catch (Exception e) {
            log.error("Failed to extract bytes from event {}: {}", event.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Convenience: extract data using the appropriate method based on datacontenttype.
     * Returns the raw string representation (JSON text, XML text, or UTF-8 decoded bytes).
     */
    public Optional<String> extractAsString(CloudEvent event) {
        if (isJson(event)) {
            return extractJson(event, Object.class).map(obj -> {
                try {
                    return jsonMapper.writeValueAsString(obj);
                } catch (Exception e) {
                    return obj.toString();
                }
            });
        }
        return extractText(event);
    }
}
