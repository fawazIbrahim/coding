package com.example.cloudevents.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sample payload for "com.example.order.*" events.
 * Supports both JSON and XML serialization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JacksonXmlRootElement(localName = "order")
public class OrderData {

    private String orderId;
    private Double amount;
    private String currency;
}
