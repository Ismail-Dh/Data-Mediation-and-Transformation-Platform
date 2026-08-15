package com.miniESB.controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.miniESB.audit.Auditable;
import com.miniESB.dto.validation.ValidationPreviewRequest;
import com.miniESB.dto.validation.ValidationPreviewResponse;
import com.miniESB.service.ValidationPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Dry-run validation endpoint — niveau 1 structural only.
 *
 * POST /api/pipelines/{pipelineId}/validation/preview
 *
 * Validates the given rawContent against the pipeline's PipelineField schema
 * WITHOUT persisting anything. Returns a structured report so the Developer
 * can iterate on the schema or the payload incrementally.
 */
@Tag(name = "Validation Preview",
        description = "Dry-run structural validation (niveau 1) — no payload is persisted")
@ConditionalOnProperty(name = "engine.mode", havingValue = "false", matchIfMissing = true)
@RestController
@RequestMapping("/api/pipelines/{pipelineId}/validation")
@PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
@RequiredArgsConstructor
public class ValidationPreviewController {

    private final ValidationPreviewService validationPreviewService;

    @Operation(
            summary = "Preview structural validation (dry-run)",
            description = """
            Tests a payload against the pipeline's schema (PipelineField definitions).
            Niveau 1 — checks field presence, null constraints, and types.
            **Nothing is persisted.** Use this to iterate on your schema or payload
            before actually submitting via POST /payloads.
            """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "Valid JSON payload",
                                            value = """
                            {
                              "rawContent": "{\\"orderId\\": 42, \\"customer\\": {\\"email\\": \\"alice@example.com\\"}}",
                              "format": "JSON"
                            }"""),
                                    @ExampleObject(name = "Missing required field",
                                            value = """
                            {
                              "rawContent": "{\\"customer\\": {\\"email\\": \\"alice@example.com\\"}}",
                              "format": "JSON"
                            }""")
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Preview result — check 'valid' and 'violations'",
                    content = @Content(schema = @Schema(implementation = ValidationPreviewResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body invalid (blank rawContent or missing format)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Pipeline not found")
    })
    @PostMapping("/preview")
    @Auditable(action = "PREVIEW", targetEntity = "Validation Preview")
    public ResponseEntity<ValidationPreviewResponse> preview(
            @PathVariable Long pipelineId,
            @Valid @RequestBody ValidationPreviewRequest request) {

        ValidationPreviewResponse result = validationPreviewService.preview(pipelineId, request);
        return ResponseEntity.ok(result);
    }
}