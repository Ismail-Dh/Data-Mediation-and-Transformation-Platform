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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Audit Logs", description = "Admin audit log consultation")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "Get all audit logs", description = "Returns audit logs with optional filters. ADMIN only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logs returned successfully"),
        @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AuditLogResponse>> getAllLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String role) {
        return ResponseEntity.ok(auditLogService.getAllLogs(username, role));
    }
}