package com.example.cloudevents.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sample payload for "com.example.payment.*" events.
 * Supports both JSON and XML serialization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JacksonXmlRootElement(localName = "payment")
public class PaymentData {

    private String paymentId;
    private String status;
    private Double amount;
}
