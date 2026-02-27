package com.example.cloudevents;

import com.example.cloudevents.model.OrderData;
import com.example.cloudevents.model.PaymentData;
import com.example.cloudevents.service.ContentTypeHandler;
import com.example.cloudevents.service.EventFileReader;
import com.example.cloudevents.service.EventFileWriter;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EventFileExchangeTest {

    @Autowired EventFileWriter writer;
    @Autowired EventFileReader reader;
    @Autowired ContentTypeHandler handler;

    @TempDir
    Path tempDir;

    @Test
    void jsonEvent_roundTrip() throws Exception {
        Path file = tempDir.resolve("json-events.ndjson");

        OrderData original = new OrderData("ORD-1", 99.99, "USD");
        CloudEvent event = writer.jsonEvent(
                UUID.randomUUID().toString(), "com.example.order.created", "/test", original);

        writer.writeAll(List.of(event), file);

        List<CloudEvent> events = reader.readAll(file);
        assertThat(events).hasSize(1);

        CloudEvent read = events.get(0);
        assertThat(read.getDataContentType()).isEqualTo("application/json");
        assertThat(handler.isJson(read)).isTrue();

        Optional<OrderData> data = handler.extractJson(read, OrderData.class);
        assertThat(data).isPresent();
        assertThat(data.get().getOrderId()).isEqualTo("ORD-1");
        assertThat(data.get().getAmount()).isEqualTo(99.99);
        assertThat(data.get().getCurrency()).isEqualTo("USD");
    }

    @Test
    void xmlEvent_roundTrip() throws Exception {
        Path file = tempDir.resolve("xml-events.ndjson");

        PaymentData original = new PaymentData("PAY-1", "SUCCESS", 49.50);
        CloudEvent event = writer.xmlEvent(
                UUID.randomUUID().toString(), "com.example.payment.processed", "/test", original);

        writer.writeAll(List.of(event), file);

        List<CloudEvent> events = reader.readAll(file);
        assertThat(events).hasSize(1);

        CloudEvent read = events.get(0);
        assertThat(read.getDataContentType()).isEqualTo("application/xml");
        assertThat(handler.isXml(read)).isTrue();

        Optional<PaymentData> data = handler.extractXml(read, PaymentData.class);
        assertThat(data).isPresent();
        assertThat(data.get().getPaymentId()).isEqualTo("PAY-1");
        assertThat(data.get().getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void textEvent_roundTrip() throws Exception {
        Path file = tempDir.resolve("text-events.ndjson");

        String message = "Hello, CloudEvents!";
        CloudEvent event = writer.textEvent(
                UUID.randomUUID().toString(), "com.example.notification.sent", "/test", message);

        writer.writeAll(List.of(event), file);

        List<CloudEvent> events = reader.readAll(file);
        assertThat(events).hasSize(1);

        CloudEvent read = events.get(0);
        assertThat(handler.isText(read)).isTrue();

        Optional<String> text = handler.extractText(read);
        assertThat(text).isPresent().contains(message);
    }

    @Test
    void binaryEvent_roundTrip() throws Exception {
        Path file = tempDir.resolve("binary-events.ndjson");

        byte[] payload = new byte[]{0x01, 0x02, 0x03, (byte) 0xFF};
        CloudEvent event = writer.binaryEvent(
                UUID.randomUUID().toString(), "com.example.audit.snapshot",
                "/test", "application/octet-stream", payload);

        writer.writeAll(List.of(event), file);

        List<CloudEvent> events = reader.readAll(file);
        assertThat(events).hasSize(1);

        Optional<byte[]> bytes = handler.extractBytes(events.get(0));
        assertThat(bytes).isPresent();
        assertThat(bytes.get()).isEqualTo(payload);
    }

    @Test
    void mixedEvents_writtenAndReadInOrder() throws Exception {
        Path file = tempDir.resolve("mixed-events.ndjson");

        CloudEvent e1 = writer.jsonEvent("id-1", "com.example.order.created", "/test",
                new OrderData("ORD-2", 10.0, "GBP"));
        CloudEvent e2 = writer.xmlEvent("id-2", "com.example.payment.processed", "/test",
                new PaymentData("PAY-2", "PENDING", 10.0));
        CloudEvent e3 = writer.textEvent("id-3", "com.example.note", "/test", "note text");

        writer.writeAll(List.of(e1, e2, e3), file);

        List<CloudEvent> events = reader.readAll(file);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getId()).isEqualTo("id-1");
        assertThat(events.get(1).getId()).isEqualTo("id-2");
        assertThat(events.get(2).getId()).isEqualTo("id-3");

        assertThat(events.get(0).getSpecVersion().toString()).isEqualTo("V1");
    }
}
