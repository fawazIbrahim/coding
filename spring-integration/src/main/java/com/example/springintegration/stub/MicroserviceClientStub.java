package com.example.springintegration.stub;

import com.example.springintegration.domain.IntegrationMessage;
import com.example.springintegration.service.MicroserviceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stub implementation of {@link MicroserviceClient}.
 *
 * <p><strong>Replace this with a real HTTP client implementation</strong>
 * (e.g., Spring WebClient or RestTemplate) that maps HTTP status codes to
 * {@link com.example.springintegration.exception.RetryableServiceException}
 * (for 5xx / 429) and
 * {@link com.example.springintegration.exception.NonRetryableServiceException}
 * (for other 4xx errors).</p>
 */
@Component
public class MicroserviceClientStub implements MicroserviceClient {

    private static final Logger log = LoggerFactory.getLogger(MicroserviceClientStub.class);

    @Override
    public Object exchange(IntegrationMessage message) {
        log.info("[STUB] MicroserviceClient.exchange called with: {}", message);
        // Stub returns a synthetic response – no network call is made.
        return "stub-microservice-response[" + message.getPayload() + "]";
    }
}
