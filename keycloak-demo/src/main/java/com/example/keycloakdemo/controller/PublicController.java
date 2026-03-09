package com.example.keycloakdemo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Public endpoints — no authentication required.
 * Accessible by anyone, including unauthenticated clients.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
            "app", "keycloak-demo",
            "description", "Spring Boot 4 + Keycloak OAuth2 Resource Server",
            "timestamp", Instant.now().toString(),
            "roles", Map.of(
                "app-user",    "Can access /api/user/**",
                "app-manager", "Can access /api/user/** and /api/manager/**",
                "app-admin",   "Can access all endpoints including /api/admin/**"
            )
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
