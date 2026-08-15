package com.miniESB.controller;

import com.miniESB.audit.Auditable;
import com.miniESB.service.EngineProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Engine Process", description = "Endpoints for processing raw content through the mediation engine")
@ConditionalOnProperty(name = "engine.mode", havingValue = "true")
@RestController
@RequestMapping("/process")
@RequiredArgsConstructor
public class EngineProcessController {

    private final EngineProcessService engineProcessService;

    // ── POST / ─────────────────────────────────────────────────────────────

    @Operation(summary = "Process raw content through the engine",
            description = """
                   Sends raw content through the engine mediation pipeline
                   (provider dispatch, mapping rules, transformation) and
                   returns the processed result.
                   """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Content processed successfully"),
            @ApiResponse(responseCode = "422",
                    description = "Invalid or unprocessable input content",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                             {
                               "error": "rawContent must not be null or empty"
                             }"""))),
            @ApiResponse(responseCode = "500",
                    description = "Processing failed due to an internal error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            examples = @ExampleObject(value = """
                             {
                               "error": "Processing failed: Connection refused"
                             }""")))
    })
    @PostMapping
    @Auditable(action = "PROCESS", targetEntity = "Engine Process")
    public ResponseEntity<?> process(@RequestBody Map<String, String> body) {
        try {
            Map<String, Object> result =
                    engineProcessService.process(body.get("rawContent"));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Processing failed: " + e.getMessage()));
        }
    }

    // ── GET /health ────────────────────────────────────────────────────────

    @Operation(summary = "Health check for the engine module",
            description = "Returns the health status of the engine process module.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Engine is up and running")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "mode", "engine"));
    }
}