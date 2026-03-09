package com.example.keycloakdemo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Manager-level endpoints — requires ROLE_APP_MANAGER or ROLE_APP_ADMIN.
 *
 * URL-level guard: set in SecurityConfig (/api/manager/** → hasAnyRole)
 * Method-level guard: @PreAuthorize for endpoint-specific refinement.
 *
 * Demo users that can access these endpoints:
 *   - bob   (app-manager)
 *   - alice (app-admin)
 *   carol (app-user) is DENIED → 403
 */
@RestController
@RequestMapping("/api/manager")
@RequiredArgsConstructor
public class ManagerController {

    // Simulated in-memory reports (would be a service + DB in a real app)
    private static final List<Map<String, Object>> REPORTS = List.of(
        Map.of("id", 1, "title", "Q1 Sales Report",    "status", "pending",  "author", "carol", "date", LocalDate.of(2024, 1, 15).toString()),
        Map.of("id", 2, "title", "Q2 Budget Forecast", "status", "approved", "author", "carol", "date", LocalDate.of(2024, 4, 10).toString()),
        Map.of("id", 3, "title", "Q3 Expense Summary", "status", "pending",  "author", "carol", "date", LocalDate.of(2024, 7, 22).toString()),
        Map.of("id", 4, "title", "Q4 Performance",     "status", "rejected", "author", "carol", "date", LocalDate.of(2024, 10, 5).toString())
    );

    /**
     * Lists all reports. Available to managers and admins.
     */
    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('APP_MANAGER', 'APP_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listReports() {
        return ResponseEntity.ok(REPORTS);
    }

    /**
     * Approves a report by ID.
     * Demonstrates that even within the /api/manager/** space, you can
     * add extra @PreAuthorize logic (e.g., only APP_MANAGER can approve, not just any manager-level role).
     */
    @PostMapping("/reports/{id}/approve")
    @PreAuthorize("hasAnyRole('APP_MANAGER', 'APP_ADMIN')")
    public ResponseEntity<Map<String, Object>> approveReport(
            @PathVariable int id,
            @AuthenticationPrincipal Jwt jwt) {

        String approvedBy = jwt.getClaimAsString("preferred_username");
        boolean found = REPORTS.stream().anyMatch(r -> r.get("id").equals(id));

        if (!found) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
            "id",         id,
            "status",     "approved",
            "approvedBy", approvedBy,
            "message",    "Report #" + id + " approved by " + approvedBy
        ));
    }

    /**
     * Lists team members under this manager.
     * Only APP_MANAGER role can see their direct team; ADMIN can see any team.
     */
    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('APP_MANAGER', 'APP_ADMIN')")
    public ResponseEntity<List<Map<String, String>>> getTeam(@AuthenticationPrincipal Jwt jwt) {
        String manager = jwt.getClaimAsString("preferred_username");

        // Simulated team data
        List<Map<String, String>> team = List.of(
            Map.of("username", "carol",   "role", "app-user",    "manager", manager),
            Map.of("username", "dave",    "role", "app-user",    "manager", manager),
            Map.of("username", "eve",     "role", "app-user",    "manager", manager)
        );
        return ResponseEntity.ok(team);
    }
}
