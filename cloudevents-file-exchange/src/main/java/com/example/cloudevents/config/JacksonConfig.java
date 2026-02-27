package com.example.cloudevents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.cloudevents.jackson.CloudEventJacksonModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    /**
     * Primary ObjectMapper with CloudEvents + Java-time support.
     * Used to serialize/deserialize CloudEvents in NDJSON files.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Registers CloudEvent serializer/deserializer.
        // forceStrict=false → data fields that are valid JSON are inlined
        // (not base64) when datacontenttype is application/json.
        mapper.registerModule(new CloudEventJacksonModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * XmlMapper for events whose datacontenttype is application/xml.
     * Kept separate from the primary ObjectMapper intentionally.
     */
    @Bean
    public XmlMapper xmlMapper() {
        XmlMapper mapper = new XmlMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
