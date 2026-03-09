package com.example.keycloakdemo.controller;

import com.example.keycloakdemo.dto.UserInfoDto;
import com.example.keycloakdemo.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * User-level endpoints — requires ROLE_APP_USER, ROLE_APP_MANAGER, or ROLE_APP_ADMIN.
 *
 * URL-level guard: set in SecurityConfig (/api/user/** → hasAnyRole)
 * Method-level guard: @PreAuthorize for fine-grained control per endpoint.
 *
 * Demo users that can access these endpoints:
 *   - carol (app-user)
 *   - bob   (app-manager)
 *   - alice (app-admin)
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserContextService userContextService;

    /**
     * Returns the authenticated user's profile extracted from JWT claims.
     * Any authenticated user can access this.
     */
    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('APP_USER', 'APP_MANAGER', 'APP_ADMIN')")
    public ResponseEntity<UserInfoDto> profile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userContextService.buildUserInfo(jwt));
    }

    /**
     * Personalized greeting using the JWT's preferred_username claim.
     */
    @GetMapping("/hello")
    @PreAuthorize("hasAnyRole('APP_USER', 'APP_MANAGER', 'APP_ADMIN')")
    public ResponseEntity<Map<String, String>> hello(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        String email    = jwt.getClaimAsString("email");
        return ResponseEntity.ok(Map.of(
            "message", "Hello, " + username + "!",
            "email",   email != null ? email : "no email in token"
        ));
    }

    /**
     * Returns own JWT claims for debugging — useful during development.
     * Restricted further to MANAGER+ so regular users can't inspect claims in detail.
     */
    @GetMapping("/token-claims")
    @PreAuthorize("hasAnyRole('APP_MANAGER', 'APP_ADMIN')")
    public ResponseEntity<Map<String, Object>> tokenClaims(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(jwt.getClaims());
    }
}
