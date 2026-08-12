package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.audit.Auditable;
import com.miniESB.dto.provider.CreateProviderRequest;
import com.miniESB.dto.provider.ProviderResponse;
import com.miniESB.dto.provider.UpdateProviderRequest;
import com.miniESB.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Providers", description = "Provider management")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @Operation(summary = "Create a provider", description = "Creates a new provider. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Provider created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid data"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "CREATE", targetEntity = "Provider")
    public ResponseEntity<ProviderResponse> createProvider(
            @Validated @RequestBody CreateProviderRequest request) {
        return new ResponseEntity<>(providerService.createProvider(request), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a provider", description = "Partial update of a provider. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider updated successfully"),
            @ApiResponse(responseCode = "404", description = "Provider not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "UPDATE", targetEntity = "Provider")
    public ResponseEntity<ProviderResponse> updateProvider(
            @PathVariable Long id,
            @RequestBody UpdateProviderRequest request) {
        return ResponseEntity.ok(providerService.updateProvider(id, request));
    }

    @Operation(summary = "Delete a provider", description = "Deletes a provider by ID. ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Provider deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Provider not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Auditable(action = "DELETE", targetEntity = "Provider")
    public ResponseEntity<Void> deleteProvider(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get provider by ID", description = "Returns a provider by ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider returned successfully"),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Auditable(action = "READ", targetEntity = "Provider")
    public ResponseEntity<ProviderResponse> getProviderById(@PathVariable Long id) {
        return ResponseEntity.ok(providerService.getProviderById(id));
    }

    @Operation(summary = "Get all providers", description = "Returns all providers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned successfully")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    @Auditable(action = "READ_ALL", targetEntity = "Provider")
    public ResponseEntity<List<ProviderResponse>> getAllProviders() {
        return ResponseEntity.ok(providerService.getAllProviders());
    }
}