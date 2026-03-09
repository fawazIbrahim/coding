package com.example.keycloakdemo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Represents the authenticated user's profile, built from JWT claims.
 */
@Data
@Builder
public class UserInfoDto {
    private String subject;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
    private Instant tokenIssuedAt;
    private Instant tokenExpiresAt;
}
