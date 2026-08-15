package com.miniESB.controller;

import com.miniESB.audit.Auditable;
import com.miniESB.dto.registry.*;
import com.miniESB.service.RegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Registry", description = "Docker registry configuration — ADMIN only")
@RestController
@RequestMapping("/api/registries")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
public class RegistryController {

    private final RegistryService registryService;

    @Operation(summary = "Create a registry")
    @PostMapping
    @Auditable(action = "CREATE", targetEntity = "Registry")
    public ResponseEntity<RegistryResponse> create(
            @Valid @RequestBody CreateRegistryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registryService.create(request));
    }

    @Operation(summary = "Update a registry")
    @PatchMapping("/{id}")
    @Auditable(action = "UPDATE", targetEntity = "Registry")
    public ResponseEntity<RegistryResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateRegistryRequest request) {
        return ResponseEntity.ok(registryService.update(id, request));
    }

    @Operation(summary = "Get registry by ID")
    @GetMapping("/{id}")
    @Auditable(action = "READ", targetEntity = "Registry")
    public ResponseEntity<RegistryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(registryService.getById(id));
    }

    @Operation(summary = "Get all registries")
    @GetMapping
    @Auditable(action = "READ_ALL", targetEntity = "Registry")
    public ResponseEntity<List<RegistryResponse>> getAll() {
        return ResponseEntity.ok(registryService.getAll());
    }

    @Operation(summary = "Delete a registry")
    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE", targetEntity = "Registry")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        registryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}