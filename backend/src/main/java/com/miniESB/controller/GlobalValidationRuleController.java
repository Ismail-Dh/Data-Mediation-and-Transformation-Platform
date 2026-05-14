package com.miniESB.controller;



import com.miniESB.audit.Auditable;
import com.miniESB.dto.globalValidationRule.GlobalValidationRuleRequestDTO;
import com.miniESB.dto.globalValidationRule.GlobalValidationRuleResponseDTO;
import com.miniESB.service.GlobalValidationRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin – Global Validation Rules",
        description = "CRUD operations on reusable global validation rules (ADMIN only)")
@RestController
@RequestMapping("/api/admin/rules")
@PreAuthorize("hasAnyRole('DEVELOPER','ADMIN')")
@RequiredArgsConstructor
public class GlobalValidationRuleController {

    private final GlobalValidationRuleService globalValidationRuleService;

    // -------------------------------------------------------------------------
    // POST /api/admin/rules
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Create a global validation rule",
            description = "Creates a reusable validation rule available to all Developers. Rule is global by default.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "NOT_NULL rule",
                                            value = """
                            {
                              "fieldName": "orderId",
                              "ruleType": "NOT_NULL",
                              "pattern": null,
                              "active": true
                            }"""),
                                    @ExampleObject(name = "REGEX_EMAIL rule",
                                            value = """
                            {
                              "fieldName": "email",
                              "ruleType": "REGEX_EMAIL",
                              "pattern": null,
                              "active": true
                            }"""),
                                    @ExampleObject(name = "REGEX_PHONE rule",
                                            value = """
                            {
                              "fieldName": "phone",
                              "ruleType": "REGEX_PHONE",
                              "pattern": null,
                              "active": true
                            }"""),
                                    @ExampleObject(name = "TYPE_NUMBER rule",
                                            value = """
                            {
                              "fieldName": "amount",
                              "ruleType": "TYPE_NUMBER",
                              "pattern": null,
                              "active": true
                            }"""),
                                    @ExampleObject(name = "TYPE_DATE rule",
                                            value = """
                            {
                              "fieldName": "birthDate",
                              "ruleType": "TYPE_DATE",
                              "pattern": null,
                              "active": true
                            }"""),
                                    @ExampleObject(name = "MIN_MAX_LENGTH rule",
                                            value = """
                            {
                              "fieldName": "username",
                              "ruleType": "MIN_MAX_LENGTH",
                              "pattern": "3,30",
                              "active": true
                            }""")
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rule created successfully",
                    content = @Content(schema = @Schema(implementation = GlobalValidationRuleResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validation error on request body"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required")
    })
    @PostMapping
    @Auditable(action = "CREATE", targetEntity = "Validation Rule")
    public ResponseEntity<GlobalValidationRuleResponseDTO> createRule(
            @Valid @RequestBody GlobalValidationRuleRequestDTO request) {
        GlobalValidationRuleResponseDTO created = globalValidationRuleService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/rules
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get all global validation rules",
            description = "Returns all global rules (active and inactive). Admin view only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of global rules"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required")
    })
    @GetMapping
    public ResponseEntity<List<GlobalValidationRuleResponseDTO>> getAllRules() {
        return ResponseEntity.ok(globalValidationRuleService.getAllRules());
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/rules/active
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get all global validation rules",
            description = "Returns all global rules active. Developer view only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of global rules"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required")
    })
    @GetMapping("/active")
    public ResponseEntity<List<GlobalValidationRuleResponseDTO>> getAllRulesActives() {
        return ResponseEntity.ok(globalValidationRuleService.getActiveRules());
    }

    // -------------------------------------------------------------------------
    // GET /api/admin/rules/{id}
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get a global validation rule by ID",
            description = "Returns a single global rule by its unique identifier."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule found"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<GlobalValidationRuleResponseDTO> getRuleById(@PathVariable Long id) {
        return ResponseEntity.ok(globalValidationRuleService.getRuleById(id));
    }

    // -------------------------------------------------------------------------
    // PUT /api/admin/rules/{id}
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Update a global validation rule",
            description = "Fully replaces the properties of a global rule. The rule remains global."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rule updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error on request body"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", targetEntity = "Validation Rule")
    public ResponseEntity<GlobalValidationRuleResponseDTO> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody GlobalValidationRuleRequestDTO request) {
        return ResponseEntity.ok(globalValidationRuleService.updateRule(id, request));
    }

    // -------------------------------------------------------------------------
    // PATCH /api/admin/rules/{id}/deactivate  — soft disable
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Deactivate a global validation rule",
            description = "Sets active=false. The rule remains in the database but is no longer visible to Developers."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rule deactivated"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Rule not found")
    })
    @PatchMapping("/{id}/deactivate")
    @Auditable(action = "DEACTIVATE", targetEntity = "Validation Rule")
    public ResponseEntity<Void> deactivateRule(@PathVariable Long id) {
        globalValidationRuleService.deactivateRule(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // DELETE /api/admin/rules/{id}
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Delete a global validation rule",
            description = """
            Permanently deletes a global rule.
            Returns **409 Conflict** if the rule is currently referenced by at least one pipeline."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rule deleted"),
            @ApiResponse(responseCode = "403", description = "Forbidden — ADMIN role required"),
            @ApiResponse(responseCode = "404", description = "Rule not found"),
            @ApiResponse(responseCode = "409", description = "Rule is still used by a pipeline and cannot be deleted")
    })
    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE", targetEntity = "Validation Rule")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        globalValidationRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}