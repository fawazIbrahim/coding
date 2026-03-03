package com.example.springintegration.service;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.domain.MappedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Maps an {@link IntegrationMessage} to a {@link MappedObject} as the first
 * step of the Type2 processing path.
 *
 * <p>If this service throws any exception, the integration flow delegates to
 * {@link ErrorHandlerService}.</p>
 */
@Service
public class Type2MappingService {

    private static final Logger log = LoggerFactory.getLogger(Type2MappingService.class);

    /**
     * Transforms the integration message into a domain-specific mapped object.
     *
     * @param message the incoming integration message
     * @return the resulting {@link MappedObject}
     * @throws RuntimeException if the mapping operation fails
     */
    public MappedObject map(IntegrationMessage message) {
        log.debug("Type2 mapping message: {}", message);
        // TODO: replace with actual mapping logic
        return new MappedObject(UUID.randomUUID().toString(), message.getPayload());
    }
}
