package com.example.keycloakdemo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Main Spring Security configuration for the Keycloak OAuth2 Resource Server.
 *
 * Two authorization strategies are used together:
 *
 *   1. ROLE-BASED  (/api/user/**, /api/manager/**, /api/admin/**)
 *      URL-level guards by realm role. Who you ARE determines access.
 *      Uses hasRole() / hasAnyRole().
 *
 *   2. PERMISSION-BASED  (/api/resources/**)
 *      URL-level guard is just "authenticated". The @PreAuthorize on each
 *      method checks the specific permission. What you CAN DO determines access.
 *      Uses hasAuthority() with the exact permission string.
 *      A user can hold a permission regardless of their realm role.
 *
 * Authorities produced by KeycloakJwtConverter:
 *   ┌────────────────────┬──────────────────────┬─────────────────────┐
 *   │ Keycloak source    │ Authority             │ Check               │
 *   ├────────────────────┼──────────────────────┼─────────────────────┤
 *   │ realm_access.roles │ ROLE_APP_ADMIN        │ hasRole('APP_ADMIN')│
 *   │ realm_access.roles │ ROLE_APP_MANAGER      │ hasRole('APP_MANAGER')
 *   │ resource_access.*  │ report:read           │ hasAuthority('report:read')
 *   │ resource_access.*  │ report:approve        │ hasAuthority('report:approve')
 *   │ resource_access.*  │ user:create           │ hasAuthority('user:create')
 *   │ resource_access.*  │ user:delete           │ hasAuthority('user:delete')
 *   └────────────────────┴──────────────────────┴─────────────────────┘
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — REST APIs with JWT don't need it
            .csrf(csrf -> csrf.disable())

            // Stateless — no HttpSession
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Authorization rules (coarse-grained, by URL pattern)
            .authorizeHttpRequests(auth -> auth

                // Public endpoints — no token required
                .requestMatchers("/api/public/**").permitAll()

                // Actuator health — useful for readiness probes
                .requestMatchers("/actuator/health").permitAll()

                // Admin endpoints — ADMIN role only
                .requestMatchers("/api/admin/**").hasRole("APP_ADMIN")

                // Manager endpoints — MANAGER or ADMIN
                .requestMatchers("/api/manager/**").hasAnyRole("APP_MANAGER", "APP_ADMIN")

                // User endpoints — any authenticated user with app-user, app-manager, or app-admin
                .requestMatchers("/api/user/**").hasAnyRole("APP_USER", "APP_MANAGER", "APP_ADMIN")

                // Permission-based endpoints — any authenticated user may call these paths;
                // the fine-grained @PreAuthorize on each method enforces the specific permission.
                // This is intentionally NOT role-gated at the URL level.
                .requestMatchers("/api/resources/**").authenticated()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Configure as OAuth2 Resource Server with JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    // Plug in our custom converter that reads Keycloak realm_access.roles
                    .jwtAuthenticationConverter(keycloakJwtConverter)
                )
            );

        return http.build();
    }
}
