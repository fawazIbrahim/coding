package com.example.springintegration.domain;

/**
 * The primary message payload routed through the integration channel.
 * The {@code type} field determines which processing path is taken.
 * Supported values: "type1", "type2", "type3".
 */
public class IntegrationMessage {

    private String type;
    private Object payload;

    public IntegrationMessage() {}

    public IntegrationMessage(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "IntegrationMessage{type='" + type + "', payload=" + payload + "}";
    }
}
