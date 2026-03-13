package com.mock.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.stream.Collectors;

@RestController
public class MockController {

    private static final Logger log = LoggerFactory.getLogger(MockController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping("/**")
    public ResponseEntity<Void> handleAll(
            HttpServletRequest request,
            @RequestBody(required = false) String rawBody) throws Exception {

        log.info("=== Incoming Request: {} {} ===", request.getMethod(), request.getRequestURI());

        // Log headers
        Collections.list(request.getHeaderNames()).forEach(name ->
                log.info("  Header: {}: {}", name, request.getHeader(name))
        );

        // Log body
        if (rawBody != null && !rawBody.isBlank()) {
            String prettyBody;
            try {
                JsonNode node = objectMapper.readTree(rawBody);
                prettyBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception e) {
                prettyBody = rawBody; // not JSON, log as-is
            }
            log.info("  Body:\n{}", prettyBody);
        } else {
            log.info("  Body: (empty)");
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
