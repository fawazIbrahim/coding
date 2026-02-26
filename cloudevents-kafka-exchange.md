# CloudEvents for Kafka — File-Based Exchange System (Spring Boot)

## Overview

A file-based exchange system where the sender writes events following the
CloudEvents for Kafka specification, and the receiver reads and parses that file.

---

## Best Format: JSON Lines (NDJSON)

Each line in the file is a self-contained CloudEvent JSON object using the
**structured content mode** (all attributes + data in one JSON object).

### Why JSON Lines?

| Criterion              | JSON Lines       | JSON Array        | Avro/Protobuf     |
|------------------------|------------------|-------------------|-------------------|
| Stream processing      | Yes (line by line)| No (full load)   | Yes               |
| Human-readable         | Yes              | Yes               | No                |
| Easy append            | Yes              | No                | No                |
| Spring Boot support    | Native Jackson   | Native Jackson    | Needs schema      |
| Large files            | Efficient        | Memory-heavy      | Efficient         |

---

## File Structure

File naming convention: `events-{source}-{yyyyMMdd-HHmmss}.ndjson`

Each line is a complete CloudEvent (CloudEvents 1.0 spec):

```jsonl
{"specversion":"1.0","id":"evt-001","type":"com.example.order.created","source":"/orders/service","time":"2026-02-26T10:00:00Z","datacontenttype":"application/json","subject":"order-123","data":{"orderId":"123","amount":99.99,"currency":"USD"}}
{"specversion":"1.0","id":"evt-002","type":"com.example.order.shipped","source":"/orders/service","time":"2026-02-26T10:05:00Z","datacontenttype":"application/json","subject":"order-123","data":{"orderId":"123","trackingNumber":"TRK-XYZ"}}
{"specversion":"1.0","id":"evt-003","type":"com.example.payment.processed","source":"/payments/service","time":"2026-02-26T10:10:00Z","datacontenttype":"application/json","subject":"payment-456","data":{"paymentId":"456","status":"SUCCESS"}}
```

### Required CloudEvents Attributes

| Attribute         | Description              | Example                         |
|-------------------|--------------------------|---------------------------------|
| `specversion`     | Always `"1.0"`           | `"1.0"`                         |
| `id`              | Unique event ID          | `"evt-001"`                     |
| `type`            | Reverse-DNS event type   | `"com.example.order.created"`   |
| `source`          | URI of producer          | `"/orders/service"`             |
| `time`            | ISO 8601 timestamp       | `"2026-02-26T10:00:00Z"`        |
| `datacontenttype` | Payload media type       | `"application/json"`            |
| `data`            | The actual event payload | `{ ... }`                       |

---

## Spring Boot Implementation

### Dependencies (`pom.xml`)

```xml
<dependency>
    <groupId>io.cloudevents</groupId>
    <artifactId>cloudevents-json-jackson</artifactId>
    <version>4.0.1</version>
</dependency>
<dependency>
    <groupId>io.cloudevents</groupId>
    <artifactId>cloudevents-core</artifactId>
    <version>4.0.1</version>
</dependency>
```

### Sender — Writes the NDJSON file

```java
@Service
public class EventFileSender {

    private final ObjectMapper objectMapper;

    public EventFileSender(ObjectMapper objectMapper) {
        objectMapper.registerModule(new CloudEventJacksonModule());
        this.objectMapper = objectMapper;
    }

    public void writeEvents(List<CloudEvent> events, Path outputFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardOpenOption.CREATE)) {
            for (CloudEvent event : events) {
                writer.write(objectMapper.writeValueAsString(event));
                writer.newLine();
            }
        }
    }

    public CloudEvent buildEvent(String id, String type, Object data) throws JsonProcessingException {
        return CloudEventBuilder.v1()
            .withId(id)
            .withType(type)
            .withSource(URI.create("/orders/service"))
            .withTime(OffsetDateTime.now())
            .withDataContentType("application/json")
            .withData(objectMapper.writeValueAsBytes(data))
            .build();
    }
}
```

### Receiver — Reads and Parses the NDJSON file

```java
@Service
public class EventFileReceiver {

    private final ObjectMapper objectMapper;

    public EventFileReceiver(ObjectMapper objectMapper) {
        objectMapper.registerModule(new CloudEventJacksonModule());
        this.objectMapper = objectMapper;
    }

    public void processFile(Path inputFile) throws IOException {
        try (Stream<String> lines = Files.lines(inputFile)) {
            lines
                .filter(line -> !line.isBlank())
                .map(this::parseLine)
                .forEach(this::handleEvent);
        }
    }

    private CloudEvent parseLine(String line) {
        try {
            return objectMapper.readValue(line, CloudEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid CloudEvent line: " + line, e);
        }
    }

    private void handleEvent(CloudEvent event) {
        switch (event.getType()) {
            case "com.example.order.created"      -> handleOrderCreated(event);
            case "com.example.payment.processed"  -> handlePaymentProcessed(event);
            default -> log.warn("Unknown event type: {}", event.getType());
        }
    }
}
```

---

## Handling Newlines Inside Payload Strings

A common concern: what if the `data` payload contains newline characters?

**Not a problem.** JSON serialization handles it automatically.

A literal newline inside a string value is always serialized as the escape
sequence `\n` (backslash + n), never as an actual newline character:

```
Actual string value:  Hello\nWorld     (contains real newline)
JSON serialized form: "Hello\\nWorld"  (escaped, stays on one line)
```

In the file, the line stays intact:

```jsonl
{"specversion":"1.0","id":"evt-001","type":"com.example.note.created","data":{"body":"Hello\nWorld\nLine 3"}}
{"specversion":"1.0","id":"evt-002","type":"com.example.note.created","data":{"body":"Another\nevent"}}
```

### Safety Summary

| Scenario                              | Safe? | Reason                        |
|---------------------------------------|-------|-------------------------------|
| String with `\n` via Jackson          | Yes   | Escaped to `\n`               |
| Nested JSON object in `data`          | Yes   | Properly serialized           |
| Binary `data` (byte[])                | Yes   | Base64 encoded                |
| Manually concatenated raw strings     | No    | Use Jackson instead           |

### The Only Real Risk

Manually constructing JSON strings without a serializer:

```java
// DANGEROUS - do not do this
String rawJson = "{\"body\": \"" + userInput + "\"}"; // userInput may have real \n

// SAFE - Jackson escapes everything correctly
objectMapper.writeValueAsString(myObject);
```

### Binary Data

If your payload is binary (e.g., Avro bytes, images), the CloudEvents SDK
Base64-encodes it automatically — no newline issues:

```java
CloudEventBuilder.v1()
    .withData("application/octet-stream", myBytes) // Base64 in JSON, safe
    .build();
```

---

## Key Design Decisions

- **File format:** `.ndjson` (also recognized as `.jsonl`)
- **Encoding:** Always UTF-8
- **File naming:** `events-{source}-{yyyyMMdd-HHmmss}.ndjson`
- **Idempotency:** The `id` field allows the receiver to deduplicate replayed files
- **Content mode:** Structured (entire CloudEvent in JSON, not binary/Kafka-header mode)
