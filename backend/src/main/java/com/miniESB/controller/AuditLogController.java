package com.miniESB.controller;

import com.miniESB.dto.auditlog.AuditLogResponse;
import com.miniESB.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Audit Logs", description = "Audit log consultation")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    // ─── ADMIN — voit tous les logs avec filtres ──────────────────────────────

    @Operation(summary = "Get all audit logs — ADMIN only")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logs returned successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLogResponse>> getAllLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer httpStatus) {
        return ResponseEntity.ok(
                auditLogService.getAllLogs(username, role, action, httpStatus)
        );
    }

    // ─── DEVELOPER — voit uniquement ses propres logs ─────────────────────────

    @Operation(summary = "Get my own audit logs — any authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logs returned successfully"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AuditLogResponse>> getMyLogs(Authentication authentication) {
        return ResponseEntity.ok(
                auditLogService.getMyLogs(authentication.getName())
        );
    }
}