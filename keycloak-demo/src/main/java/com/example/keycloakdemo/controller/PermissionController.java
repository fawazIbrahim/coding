package com.example.keycloakdemo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Permission-based endpoints under /api/resources/**
 *
 * KEY DIFFERENCE FROM ROLE-BASED CONTROLLERS:
 *   - The URL pattern (/api/resources/**) is only guarded by "authenticated()" in SecurityConfig.
 *   - Access is granted or denied purely by the specific permission string in the JWT,
 *     set as a client role in resource_access.company-api.roles.
 *   - A user's realm role (app-user, app-manager, app-admin) is IRRELEVANT here.
 *
 * This means:
 *   - carol (app-user) CAN read reports    → she has 'report:read'
 *   - carol (app-user) CANNOT approve them → she does NOT have 'report:approve'
 *   - bob   (app-manager) CAN approve      → he has 'report:approve'
 *   - dave  (app-user)   CANNOT read       → he was not granted 'report:read'
 *
 * Permission matrix (see company-realm-export.json):
 *
 *   Permission        │ alice (admin) │ bob (manager) │ carol (user)
 *   ──────────────────┼───────────────┼───────────────┼─────────────
 *   report:read       │      ✓        │      ✓        │      ✓
 *   report:approve    │      ✓        │      ✓        │      ✗
 *   user:create       │      ✓        │      ✗        │      ✗
 *   user:delete       │      ✓        │      ✗        │      ✗
 *   admin:realm-view  │      ✓        │      ✗        │      ✗
 */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class PermissionController {

    // Simulated data store
    private static final List<Map<String, Object>> REPORTS = List.of(
        Map.of("id", "r-001", "title", "Annual Budget",   "status", "pending",  "amount", 120_000),
        Map.of("id", "r-002", "title", "Q1 Travel Costs", "status", "pending",  "amount", 4_500),
        Map.of("id", "r-003", "title", "Equipment Order", "status", "approved", "amount", 35_000)
    );

    // -------------------------------------------------------------------------
    // REPORT PERMISSIONS
    // -------------------------------------------------------------------------

    /**
     * GET /api/resources/reports
     * Required permission: report:read
     *
     * Who can call this:
     *   ✓ alice  (has report:read)
     *   ✓ bob    (has report:read)
     *   ✓ carol  (has report:read  ← even though she is only app-user)
     *   ✗ anyone without report:read in their token → 403
     */
    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('report:read')")
    public ResponseEntity<List<Map<String, Object>>> readReports() {
        return ResponseEntity.ok(REPORTS);
    }

    /**
     * POST /api/resources/reports/{id}/approve
     * Required permission: report:approve
     *
     * Who can call this:
     *   ✓ alice  (has report:approve)
     *   ✓ bob    (has report:approve)
     *   ✗ carol  (has report:read but NOT report:approve) → 403
     *
     * Note: carol can READ above but CANNOT approve — same role (app-user),
     * different permissions. This is impossible to express with roles alone.
     */
    @PostMapping("/reports/{id}/approve")
    @PreAuthorize("hasAuthority('report:approve')")
    public ResponseEntity<Map<String, Object>> approveReport(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        boolean exists = REPORTS.stream().anyMatch(r -> r.get("id").equals(id));
        if (!exists) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "id",         id,
            "status",     "approved",
            "approvedBy", jwt.getClaimAsString("preferred_username"),
            "permission", "report:approve"   // shows which permission granted access
        ));
    }

    // -------------------------------------------------------------------------
    // USER MANAGEMENT PERMISSIONS
    // -------------------------------------------------------------------------

    /**
     * POST /api/resources/users
     * Required permission: user:create
     *
     * Who can call this:
     *   ✓ alice  (has user:create)
     *   ✗ bob    (does NOT have user:create, even though he's app-manager) → 403
     *   ✗ carol  → 403
     */
    @PostMapping("/users")
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {

        String newUsername = body.get("username");
        if (newUsername == null || newUsername.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username required"));
        }

        return ResponseEntity.status(201).body(Map.of(
            "id",        "u-" + UUID.randomUUID().toString().substring(0, 6),
            "username",  newUsername,
            "createdBy", jwt.getClaimAsString("preferred_username"),
            "permission", "user:create"
        ));
    }

    /**
     * DELETE /api/resources/users/{username}
     * Required permission: user:delete
     *
     * Who can call this:
     *   ✓ alice  (has user:delete)
     *   ✗ bob    → 403
     *   ✗ carol  → 403
     */
    @DeleteMapping("/users/{username}")
    @PreAuthorize("hasAuthority('user:delete')")
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable String username,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(Map.of(
            "deleted",   username,
            "deletedBy", jwt.getClaimAsString("preferred_username"),
            "permission", "user:delete"
        ));
    }

    // -------------------------------------------------------------------------
    // COMBINING PERMISSIONS — both required
    // -------------------------------------------------------------------------

    /**
     * POST /api/resources/reports/{id}/archive
     * Required permissions: report:read AND report:approve (both needed)
     *
     * Demonstrates combining multiple permission checks in one expression.
     * Only users who can both read AND approve reports may archive them.
     */
    @PostMapping("/reports/{id}/archive")
    @PreAuthorize("hasAuthority('report:read') and hasAuthority('report:approve')")
    public ResponseEntity<Map<String, Object>> archiveReport(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(Map.of(
            "id",        id,
            "status",    "archived",
            "archivedBy", jwt.getClaimAsString("preferred_username"),
            "required",  List.of("report:read", "report:approve")
        ));
    }

    // -------------------------------------------------------------------------
    // PERMISSION SELF-INSPECTION
    // -------------------------------------------------------------------------

    /**
     * GET /api/resources/my-permissions
     * Required: just authenticated
     *
     * Returns the resolved permission authorities from the current JWT.
     * Useful for frontends to conditionally show/hide UI elements.
     */
    @GetMapping("/my-permissions")
    public ResponseEntity<Map<String, Object>> myPermissions(@AuthenticationPrincipal Jwt jwt) {
        // Spring Security resolved the authorities — pull them from SecurityContext
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();

        List<String> permissions = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                // Permissions are stored without ROLE_ prefix; roles have it
                .filter(a -> !a.startsWith("ROLE_") && !a.startsWith("SCOPE_"))
                .sorted()
                .toList();

        List<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_APP_"))
                .sorted()
                .toList();

        return ResponseEntity.ok(Map.of(
            "username",    jwt.getClaimAsString("preferred_username"),
            "roles",       roles,
            "permissions", permissions
        ));
    }
}
