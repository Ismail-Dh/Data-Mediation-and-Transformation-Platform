package com.miniESB.controller;

import com.miniESB.audit.Auditable;
import com.miniESB.domain.enums.TemplateType;
import com.miniESB.dto.template.TemplateRequest;
import com.miniESB.dto.template.TemplateResponse;
import com.miniESB.service.TemplateAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only endpoints for managing ValidationTemplate and MappingTemplate.
 *
 * Base path : /api/admin/templates
 * Security  : ROLE_ADMIN (JWT)
 *
 * Lifecycle : DRAFT → PUBLISHED → DISABLED
 * Immutability : updating a PUBLISHED template forks a new DRAFT version.
 */
@Tag(name = "Admin – Templates",
        description = "CRUD operations on ValidationTemplate and MappingTemplate (ADMIN only). "
                + "Templates follow the lifecycle DRAFT → PUBLISHED → DISABLED. "
                + "Updating a PUBLISHED template is immutable: a new DRAFT version is forked.")
@RestController
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RequestMapping("/api/admin/templates")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminTemplateController {

    private final TemplateAdminService templateService;

    // -------------------------------------------------------------------------
    // POST /api/admin/templates
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Create a template (DRAFT)",
            description = "Creates a new VALIDATION or MAPPING template in DRAFT state.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "Validation template",
                                            value = """
                                    {
                                      "name": "Standard Order Validation",
                                      "description": "Validates required order fields",
                                      "type": "VALIDATION",
                                      "content": {
                                        "rules": [
                                          { "field": "orderId",  "ruleType": "NOT_NULL" },
                                          { "field": "email",    "ruleType": "REGEX_EMAIL" },
                                          { "field": "amount",   "ruleType": "TYPE_NUMBER" }
                                        ]
                                      }
                                    }"""),
                                    @ExampleObject(name = "Mapping template",
                                            value = """
                                    {
                                      "name": "CRM → ERP Mapping",
                                      "description": "Maps CRM payload fields to ERP schema",
                                      "type": "MAPPING",
                                      "content": {
                                        "mappings": [
                                          { "source": "firstName", "target": "first_name", "type": "DIRECT" },
                                          { "source": "birthDate", "target": "dob",        "type": "DATE_FORMAT",
                                            "expression": "yyyy-MM-dd" }
                                        ]
                                      }
                                    }""")
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Template created",
                    content = @Content(schema = @Schema(implementation = TemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required")
    })
    @PostMapping
    @Auditable(action = "CREATE_TEMPLATE", targetEntity = "Template")
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody TemplateRequest request,
            Authentication auth) {

        TemplateResponse created = templateService.create(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/templates?type=VALIDATION|MAPPING
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List all templates (Admin)",
            description = "Returns all templates of the given type regardless of status (DRAFT / PUBLISHED / DISABLED)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of templates"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required")
    })
    @GetMapping
    @Auditable(action = "LIST_TEMPLATES", targetEntity = "Template")
    public ResponseEntity<List<TemplateResponse>> findAll(
            @RequestParam TemplateType type) {

        return ResponseEntity.ok(templateService.findAll(type));
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/templates/{id}?type=VALIDATION|MAPPING
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get template by ID (Admin)",
            description = "Returns a single template regardless of its status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template found"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @GetMapping("/{id}")
    @Auditable(action = "GET_TEMPLATE", targetEntity = "Template")
    public ResponseEntity<TemplateResponse> findById(
            @PathVariable Long id,
            @RequestParam TemplateType type) {

        return ResponseEntity.ok(templateService.findById(id, type));
    }

    // -------------------------------------------------------------------------
    // PUT /api/admin/templates/{id}?type=VALIDATION|MAPPING
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Update a template",
            description = """
                    Updates a DRAFT template in place.
                    If the template is **PUBLISHED**, immutability applies:
                    the existing record is left unchanged and a new DRAFT version is created
                    (with `version` incremented and `parentId` pointing to the current template).
                    DISABLED templates cannot be updated."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template updated (or new version created)"),
            @ApiResponse(responseCode = "400", description = "Validation error or template is DISABLED"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PutMapping("/{id}")
    @Auditable(action = "UPDATE_TEMPLATE", targetEntity = "Template")
    public ResponseEntity<TemplateResponse> update(
            @PathVariable Long id,
            @RequestParam TemplateType type,
            @Valid @RequestBody TemplateRequest request,
            Authentication auth) {

        return ResponseEntity.ok(templateService.update(id, type, request, auth.getName()));
    }

    // -------------------------------------------------------------------------
    // PUT /api/admin/templates/{id}/publish?type=VALIDATION|MAPPING
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Publish a template (DRAFT → PUBLISHED)",
            description = "Transitions a DRAFT template to PUBLISHED state. Only DRAFT templates can be published."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template published"),
            @ApiResponse(responseCode = "400", description = "Invalid state transition"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PutMapping("/{id}/publish")
    @Auditable(action = "PUBLISH_TEMPLATE", targetEntity = "Template")
    public ResponseEntity<TemplateResponse> publish(
            @PathVariable Long id,
            @RequestParam TemplateType type) {

        return ResponseEntity.ok(templateService.publish(id, type));
    }

    // -------------------------------------------------------------------------
    // PUT /api/admin/templates/{id}/disable?type=VALIDATION|MAPPING
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Disable a template (PUBLISHED → DISABLED)",
            description = "Transitions a PUBLISHED template to DISABLED state. Disabled templates are hidden from Developers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template disabled"),
            @ApiResponse(responseCode = "400", description = "Invalid state transition"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Template not found")
    })
    @PutMapping("/{id}/disable")
    @Auditable(action = "DISABLE_TEMPLATE", targetEntity = "Template")
    public ResponseEntity<TemplateResponse> disable(
            @PathVariable Long id,
            @RequestParam TemplateType type) {

        return ResponseEntity.ok(templateService.disable(id, type));
    }
}