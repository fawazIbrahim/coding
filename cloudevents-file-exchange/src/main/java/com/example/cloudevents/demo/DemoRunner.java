package com.example.cloudevents.demo;

import com.example.cloudevents.model.OrderData;
import com.example.cloudevents.model.PaymentData;
import com.example.cloudevents.service.ContentTypeHandler;
import com.example.cloudevents.service.EventFileReader;
import com.example.cloudevents.service.EventFileWriter;
import io.cloudevents.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Demonstrates writing and reading CloudEvents with different datacontenttype values:
 *   - application/json  (data inlined as a JSON object in the NDJSON line)
 *   - application/xml   (data stored base64-encoded in data_base64)
 *   - text/plain        (data stored base64-encoded in data_base64)
 *   - application/octet-stream (raw binary, base64-encoded)
 */
@Slf4j
@Component
public class DemoRunner implements CommandLineRunner {

    private final EventFileWriter writer;
    private final EventFileReader reader;
    private final ContentTypeHandler handler;

    public DemoRunner(EventFileWriter writer, EventFileReader reader, ContentTypeHandler handler) {
        this.writer = writer;
        this.reader = reader;
        this.handler = handler;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== CloudEvents File Exchange Demo ===");

        // ── 1. Build events with different content types ────────────────────
        List<CloudEvent> events = new ArrayList<>();

        // JSON event — data is an inline JSON object in the file
        OrderData order = new OrderData("ORD-001", 149.99, "EUR");
        events.add(writer.jsonEvent(
                UUID.randomUUID().toString(),
                "com.example.order.created",
                "/orders/service",
                order));

        // XML event — data is XML bytes stored as data_base64
        PaymentData payment = new PaymentData("PAY-001", "SUCCESS", 149.99);
        events.add(writer.xmlEvent(
                UUID.randomUUID().toString(),
                "com.example.payment.processed",
                "/payments/service",
                payment));

        // Text event — plain string stored as data_base64
        events.add(writer.textEvent(
                UUID.randomUUID().toString(),
                "com.example.notification.sent",
                "/notifications/service",
                "Order ORD-001 has been confirmed and payment received."));

        // Binary event — arbitrary bytes stored as data_base64
        byte[] binaryPayload = new byte[]{0x01, 0x02, 0x03, (byte) 0xFF};
        events.add(writer.binaryEvent(
                UUID.randomUUID().toString(),
                "com.example.audit.snapshot",
                "/audit/service",
                "application/octet-stream",
                binaryPayload));

        // ── 2. Write to NDJSON file ─────────────────────────────────────────
        Path file = writer.createOutputFile("demo");
        writer.writeAll(events, file);
        log.info("Written file: {}", file.toAbsolutePath());

        // ── 3. Read back from file ─────────────────────────────────────────
        log.info("");
        log.info("--- Reading events back ---");
        List<CloudEvent> readEvents = reader.readAll(file);

        for (CloudEvent event : readEvents) {
            log.info("");
            log.info("Event id={} type={} datacontenttype={}",
                    event.getId(), event.getType(), event.getDataContentType());

            if (handler.isJson(event)) {
                handler.extractJson(event, OrderData.class)
                        .ifPresent(o -> log.info("  [JSON] order: {}", o));

            } else if (handler.isXml(event)) {
                handler.extractXml(event, PaymentData.class)
                        .ifPresent(p -> log.info("  [XML] payment: {}", p));

            } else if (handler.isText(event)) {
                handler.extractText(event)
                        .ifPresent(t -> log.info("  [TEXT] message: {}", t));

            } else {
                handler.extractBytes(event)
                        .ifPresent(b -> log.info("  [BINARY] {} bytes", b.length));
            }
        }

        log.info("");
        log.info("=== Demo complete. File: {} ===", file.toAbsolutePath());
    }
}
