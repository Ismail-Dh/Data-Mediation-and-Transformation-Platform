package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.domain.enums.TemplateType;
import com.miniESB.dto.template.TemplateResponse;
import com.miniESB.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Developer-facing read-only endpoints for templates.
 *
 * Base path : /api/templates
 * Security  : Any authenticated user (ADMIN or DEVELOPER)
 * Visibility: Only PUBLISHED templates are returned.
 */
@Tag(name = "Developer – Templates",
        description = "Read-only access to PUBLISHED templates for use in pipeline configuration.")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/templates")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DeveloperTemplateController {

    private final TemplateService templateService;

    // -------------------------------------------------------------------------
    // GET /api/templates?type=VALIDATION|MAPPING
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List published templates",
            description = "Returns all templates of the given type that are in PUBLISHED state. "
                    + "DRAFT and DISABLED templates are excluded."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of published templates",
                    content = @Content(schema = @Schema(implementation = TemplateResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized — valid JWT required")
    })
    @GetMapping
    public ResponseEntity<List<TemplateResponse>> findPublished(
            @RequestParam TemplateType type) {

        return ResponseEntity.ok(templateService.findPublished(type));
    }
}