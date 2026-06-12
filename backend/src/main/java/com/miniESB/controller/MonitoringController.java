package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.dto.monitoring.MonitoringStatsResponse;
import com.miniESB.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Monitoring", description = "Admin KPI dashboard")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/admin/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @Operation(
            summary     = "Get platform KPI stats — ADMIN only",
            description = "Returns total requests, error rate, avg duration, pipeline counts, "
                    + "payload breakdown, hourly time-series (last 24 h), and per-action stats."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stats returned successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied — ADMIN role required")
    })
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MonitoringStatsResponse> getStats() {
        return ResponseEntity.ok(monitoringService.getStats());
    }
}