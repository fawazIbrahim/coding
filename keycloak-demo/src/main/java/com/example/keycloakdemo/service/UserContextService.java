package com.example.keycloakdemo.service;

import com.example.keycloakdemo.dto.UserInfoDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Helper service for extracting user information from the JWT and SecurityContext.
 *
 * Centralizes JWT claim access so controllers stay thin.
 * In a real app this might also call Keycloak's Admin API for enriched user data.
 */
@Service
public class UserContextService {

    /**
     * Builds a UserInfoDto from a JWT — covers standard OpenID Connect claims
     * that Keycloak includes by default.
     */
    public UserInfoDto buildUserInfo(Jwt jwt) {
        List<String> roles = extractRealmRoles(jwt);

        return UserInfoDto.builder()
            .subject(jwt.getSubject())
            .username(jwt.getClaimAsString("preferred_username"))
            .email(jwt.getClaimAsString("email"))
            .firstName(jwt.getClaimAsString("given_name"))
            .lastName(jwt.getClaimAsString("family_name"))
            .roles(roles)
            .tokenIssuedAt(jwt.getIssuedAt())
            .tokenExpiresAt(jwt.getExpiresAt())
            .build();
    }

    /**
     * Returns the username of the currently authenticated user from the SecurityContext.
     * Works anywhere in the call stack without needing @AuthenticationPrincipal injection.
     */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return auth.getName();
    }

    /**
     * Returns the granted authorities of the current user as strings.
     */
    public List<String> getCurrentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    /**
     * Checks whether the current user has a specific realm role.
     * Example: hasRealmRole("APP_ADMIN")
     */
    public boolean hasRealmRole(String role) {
        return getCurrentAuthorities().contains("ROLE_" + role);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return List.of();
        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
