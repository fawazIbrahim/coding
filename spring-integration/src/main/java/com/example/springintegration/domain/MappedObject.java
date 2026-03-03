package com.example.springintegration.domain;

/**
 * The result of the Type2 mapping service.
 * Produced by {@link com.example.springintegration.service.Type2MappingService}
 * and forwarded to the Kafka producer.
 */
public class MappedObject {

    private final String id;
    private final Object mappedData;

    public MappedObject(String id, Object mappedData) {
        this.id = id;
        this.mappedData = mappedData;
    }

    public String getId() {
        return id;
    }

    public Object getMappedData() {
        return mappedData;
    }

    @Override
    public String toString() {
        return "MappedObject{id='" + id + "', mappedData=" + mappedData + "}";
    }
}
