package com.example.keycloakdemo.controller;

import com.example.keycloakdemo.dto.RealmConfigDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin-level endpoints — requires ROLE_APP_ADMIN only.
 *
 * URL-level guard: set in SecurityConfig (/api/admin/** → hasRole("APP_ADMIN"))
 * Method-level guard: @PreAuthorize for extra safety and documentation.
 *
 * Only alice (app-admin) can access these endpoints.
 * bob (app-manager) and carol (app-user) are DENIED → 403.
 *
 * Demonstrates:
 *   - CRUD-style endpoints with role enforcement
 *   - Accessing the SecurityContext directly
 *   - Realm configuration summary endpoint
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    // Simulated user store (in-memory — replace with a real DB/service)
    private final ConcurrentHashMap<String, Map<String, Object>> userStore = new ConcurrentHashMap<>(Map.of(
        "alice", Map.of("id", "u-001", "username", "alice", "email", "alice@company.com", "role", "app-admin",   "active", true),
        "bob",   Map.of("id", "u-002", "username", "bob",   "email", "bob@company.com",   "role", "app-manager", "active", true),
        "carol", Map.of("id", "u-003", "username", "carol", "email", "carol@company.com", "role", "app-user",    "active", true)
    ));

    /**
     * Lists all users in the system.
     * ADMIN only.
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        return ResponseEntity.ok(new ArrayList<>(userStore.values()));
    }

    /**
     * Creates a new user.
     * ADMIN only. In a real app this would call Keycloak's Admin REST API.
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal Jwt jwt) {

        String username = request.get("username");
        String email    = request.get("email");
        String role     = request.getOrDefault("role", "app-user");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username is required"));
        }
        if (userStore.containsKey(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "User already exists: " + username));
        }

        Map<String, Object> newUser = Map.of(
            "id",       "u-" + UUID.randomUUID().toString().substring(0, 6),
            "username", username,
            "email",    email != null ? email : username + "@company.com",
            "role",     role,
            "active",   true,
            "createdBy", jwt.getClaimAsString("preferred_username")
        );

        userStore.put(username, newUser);
        return ResponseEntity.status(201).body(newUser);
    }

    /**
     * Deletes a user by username.
     * ADMIN only. Guards against self-deletion for extra safety.
     */
    @DeleteMapping("/users/{username}")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable String username,
            @AuthenticationPrincipal Jwt jwt) {

        String currentUser = jwt.getClaimAsString("preferred_username");
        if (username.equals(currentUser)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete your own account"));
        }

        if (!userStore.containsKey(username)) {
            return ResponseEntity.notFound().build();
        }

        userStore.remove(username);
        return ResponseEntity.ok(Map.of(
            "message",   "User '" + username + "' deleted",
            "deletedBy", currentUser
        ));
    }

    /**
     * Returns a summary of the current realm configuration.
     * Demonstrates injecting realm info from JWT claims (iss claim) and config.
     */
    @GetMapping("/realm-config")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<RealmConfigDto> realmConfig(@AuthenticationPrincipal Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        RealmConfigDto config = RealmConfigDto.builder()
            .realm("company-realm")
            .issuerUri(issuer)
            .clients(List.of("company-api", "company-frontend"))
            .realmRoles(List.of("app-admin", "app-manager", "app-user"))
            .groups(List.of("admins", "managers", "users"))
            .demoUsers(List.of(
                Map.of("username", "alice", "role", "app-admin",   "permissions", "report:read, report:approve, user:create, user:delete, admin:realm-view"),
                Map.of("username", "bob",   "role", "app-manager", "permissions", "report:read, report:approve"),
                Map.of("username", "carol", "role", "app-user",    "permissions", "report:read")
            ))
            .build();

        return ResponseEntity.ok(config);
    }

    /**
     * Returns the current admin's security context.
     * Useful for debugging what authorities Spring Security resolved.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('APP_ADMIN')")
    public ResponseEntity<Map<String, Object>> currentAdminContext(
            @AuthenticationPrincipal Jwt jwt,
            @CurrentSecurityContext SecurityContext context) {

        var authorities = context.getAuthentication().getAuthorities()
                .stream().map(Object::toString).toList();

        return ResponseEntity.ok(Map.of(
            "username",    jwt.getClaimAsString("preferred_username"),
            "subject",     jwt.getSubject(),
            "email",       jwt.getClaimAsString("email"),
            "authorities", authorities,
            "tokenIssuedAt",  jwt.getIssuedAt(),
            "tokenExpiresAt", jwt.getExpiresAt()
        ));
    }
}
