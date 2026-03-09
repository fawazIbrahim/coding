package com.example.keycloakdemo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converts a Keycloak JWT into a Spring Security authentication token.
 *
 * Keycloak stores roles in two places inside the JWT:
 *   1. realm_access.roles          — realm-level roles (e.g. app-admin, app-manager, app-user)
 *   2. resource_access.<id>.roles  — client-level roles used as fine-grained permissions
 *
 * Two types of GrantedAuthority are produced:
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  Source                  │  Authority format    │  Check with    │
 *   ├──────────────────────────┼──────────────────────┼────────────────┤
 *   │  realm_access.roles      │  ROLE_APP_ADMIN      │  hasRole()     │
 *   │  resource_access.*.roles │  report:approve      │  hasAuthority()│
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * This separation is the key distinction:
 *   - Roles  → coarse-grained, who you ARE  (admin, manager, user)
 *   - Permissions → fine-grained, what you CAN DO (report:approve, user:create)
 *
 * A user can have a permission without having the matching role, and vice versa.
 * For example, carol (app-user) can hold report:read without being app-manager.
 */
@Component
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Value("${keycloak.client-id}")
    private String clientId;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultConverter.convert(jwt).stream(),
                Stream.concat(
                        extractRealmRoles(jwt).stream(),
                        extractPermissions(jwt, clientId).stream()
                )
        ).collect(Collectors.toSet());

        String principalName = jwt.getClaimAsString("preferred_username");
        if (principalName == null) {
            principalName = jwt.getSubject();
        }

        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }

    /**
     * Realm roles → ROLE_* authorities (used with hasRole()).
     *
     * JWT: { "realm_access": { "roles": ["app-admin"] } }
     *  →   GrantedAuthority("ROLE_APP_ADMIN")
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase().replace("-", "_")))
                .collect(Collectors.toList());
    }

    /**
     * Client roles → plain permission authorities (used with hasAuthority()).
     *
     * JWT: { "resource_access": { "company-api": { "roles": ["report:approve", "user:create"] } } }
     *  →   GrantedAuthority("report:approve"), GrantedAuthority("user:create")
     *
     * No prefix is added — permissions are checked by their exact name.
     * This allows hasAuthority('report:approve') to work regardless of realm role.
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractPermissions(Jwt jwt, String clientId) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null || !resourceAccess.containsKey(clientId)) {
            return Collections.emptyList();
        }

        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(clientId);
        if (clientAccess == null || !clientAccess.containsKey("roles")) {
            return Collections.emptyList();
        }

        List<String> permissions = (List<String>) clientAccess.get("roles");
        // Keep the permission name as-is (e.g. "report:approve") — no ROLE_ prefix.
        // hasAuthority('report:approve') matches the exact string.
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
