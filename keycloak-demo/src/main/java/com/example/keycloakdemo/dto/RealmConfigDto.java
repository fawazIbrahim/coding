package com.example.keycloakdemo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Summary of the Keycloak realm configuration for admin inspection.
 */
@Data
@Builder
public class RealmConfigDto {
    private String realm;
    private String issuerUri;
    private List<String> clients;
    private List<String> realmRoles;
    private List<String> groups;
    private List<Map<String, String>> demoUsers;
}
